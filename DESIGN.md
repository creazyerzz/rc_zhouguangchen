# API 通知系统设计文档

## 一、问题理解

### 1.1 业务场景

企业内部多个业务系统在关键事件发生时，需要调用外部系统供应商提供的 HTTP API 进行通知。

### 1.2 核心问题

> **业务系统告诉你："把通知送到就行，别让我操心。"**

最核心的问题：
- **接收通知 → 可靠持久化 → 异步投递 → 记录状态**

---

## 二、系统边界

### 2.1 在本系统中解决

| 职责 | 说明 |
|------|------|
| 统一入口 | 为业务系统提供标准化的 HTTP API |
| 可靠持久化 | MySQL 存储任务，防止数据丢失 |
| 异步投递 | 新任务和重试任务分离投递 |
| 重试机制 | 失败任务冷却后重新拉取 |
| 状态追踪 | 提供任务状态查询接口 |

### 2.2 明确不解决

| 放弃项 | 原因 |
|--------|------|
| ~~消息队列中间件~~ | MySQL 作为消息源，足够且简单 |
| ~~熔断保护~~ | 简单重试足够 |
| ~~限流~~ | MVP 阶段不需要 |

---

## 三、可靠性与失败处理

### 3.1 投递语义：至少一次（At-Least-Once）

**选择理由**：
- 业务场景允许重复通知
- 实现相对简单，重试机制即可满足

### 3.2 重试策略

**核心设计**：新任务和重试任务**完全分离**

| 任务类型 | 拉取条件 | 说明 |
|----------|----------|------|
| 新任务 | retry_count = 0 | 持续拉取，优先处理 |
| 重试任务 | retry_count > 0 且冷却 60 秒 | 定时拉取，避免阻塞 |

### 3.3 状态流转

```
PENDING → SUCCESS (成功)
PENDING → PENDING (失败，等待冷却后重试)
PENDING → FAILED (超过最大重试)
```

---

## 四、架构设计

### 4.1 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                    业务系统 (Sender)                         │
└───────────────────────┬─────────────────────────────────────┘
                        │ HTTP POST /api/notify
                        ▼
┌─────────────────────────────────────────────────────────────┐
│              NotificationController                         │
│         POST /api/notify (接收请求)                         │
│         GET  /api/notify/{taskId} (查询状态)                │
└───────────────────────┬─────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────────┐
│              NotificationService                            │
│  1. 校验目标系统                                             │
│  2. 持久化到 MySQL (status=PENDING, retry_count=0)         │
│  3. 返回 taskId                                             │
└───────────────────────┬─────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────────┐
│                    MySQL 任务表                              │
│  status=PENDING, retry_count < max_retries                 │
└───────────────────────┬─────────────────────────────────────┘
                        │
        ┌───────────────┴───────────────┐
        ▼                               ▼
┌───────────────────────────┐   ┌───────────────────────────┐
│  新任务线程               │   │  重试调度器               │
│  持续拉取 retry_count=0   │   │  每 60 秒拉取一次         │
│  → newTaskQueue          │   │  → retryTaskQueue         │
└───────────────────────────┘   └───────────────────────────┘
        │                               │
        │                               │
        └───────────────┬───────────────┘
                        ▼
┌─────────────────────────────────────────────────────────────┐
│              统一工作线程池 (N 个 worker)                      │
│  优先消费 newTaskQueue → 其次 retryTaskQueue                │
└───────────────────────┬─────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────────┐
│              NotificationDelivery                           │
│  HTTP 投递到外部系统                                         │
│  成功 → 更新 MySQL (status=SUCCESS)                        │
│  失败 → 更新 MySQL (retry_count++, 等下次冷却后拉取)         │
└─────────────────────────────────────────────────────────────┘
```

### 4.2 核心组件

| 组件 | 类型 | 说明 |
|------|------|------|
| new-task-fetcher | 分配线程 | 持续拉取新任务到 newTaskQueue |
| retry-scheduler | 调度器 | 每 60 秒拉取重试任务到 retryTaskQueue |
| newTaskQueue | 内存队列 | 新任务队列，优先级高 |
| retryTaskQueue | 内存队列 | 重试任务队列，优先级低 |
| worker 线程池 | ExecutorService | 统一消费两个队列 |

### 4.3 配置参数

```yaml
notify:
  worker:
    thread-count: 3              # 工作线程数
    fetch-batch-size: 50       # 每次拉取数量
    queue-capacity: 1000        # 内存队列容量
    retry-delay-seconds: 60     # 重试冷却时间（秒）
```

---

## 五、线程模型

### 5.1 线程分配

| 线程 | 数量 | 职责 |
|------|------|------|
| main | 1 | Spring Boot 主线程 |
| new-task-fetcher | 1 | 持续拉取新任务 |
| retry-scheduler | 1 | 定时拉取重试任务 |
| worker-0,1,2 | N | 工作线程，执行投递 |
| mysql-cleanup | - | MySQL 连接池线程 |

### 5.2 数据流

```
新任务线程              newTaskQueue          工作线程
    │                      │                    │
    │──拉取新任务────────▶│                    │
    │                      │                    │
    │                      │◀──poll()────────│
    │                      │                    │
    │                      │                    │──HTTP 投递
    │                      │                    │
    │                      │                    │──UPDATE MySQL


重试调度器             retryTaskQueue        工作线程
    │                      │                    │
    │──每60秒拉取────────▶│                    │
    │                      │                    │
    │                      │◀──poll()────────│
    │                      │                    │
    │                      │                    │──HTTP 投递
    │                      │                    │
    │                      │                    │──UPDATE MySQL
```

---

## 六、重试机制

### 6.1 实现原理

**冷却机制**：通过数据库 updated_at 时间控制

```sql
-- 新任务拉取
SELECT * FROM notification_task
WHERE status = 'PENDING'
  AND retry_count = 0
ORDER BY created_at ASC
LIMIT 50

-- 重试任务拉取（需要冷却 60 秒）
SELECT * FROM notification_task
WHERE status = 'PENDING'
  AND retry_count > 0
  AND retry_count < max_retries
  AND updated_at <= DATE_SUB(NOW(), INTERVAL 60 SECOND)
ORDER BY retry_count ASC, updated_at ASC
LIMIT 50
```

### 6.2 失败处理

```java
private void handleFailure(NotificationTask task, String errorMessage) {
    int currentRetry = task.getRetryCount();
    int maxRetries = task.getMaxRetries();

    if (currentRetry < maxRetries) {
        // 更新重试次数，updated_at 自动更新
        taskMapper.updateRetryCount(task.getId(), currentRetry + 1, errorMessage);
        // 等待冷却后被 retry-scheduler 拉取
    } else {
        taskMapper.updateStatusWithError(task.getId(), TaskStatus.FAILED, ...);
    }
}
```

### 6.3 重试冷却

- 默认冷却时间：60 秒
- 重试次数越多，冷却时间不变（始终 60 秒）
- 可通过配置调整

---

## 七、与旧架构对比

### 7.1 旧架构（单一队列）

```
分配线程 ──拉取所有 PENDING──▶ 队列 ──▶ 工作线程
                                        │
                            失败 ────▶ retry_count++
                                        │
                              立即重新入队（无冷却）
```

**问题**：失败任务会抢占新任务的资源

### 7.2 新架构（分离队列）

```
新任务线程 ──拉取 retry_count=0──▶ newTaskQueue ──▶ 工作线程
                                                        │
重试调度器 ──每60秒拉取──▶ retryTaskQueue ──────────▶ │
        │                                              │
        │                                              │
        ◀───────────── retry_count++ ◀───── 失败 ◀───┘
```

**优势**：
- ✅ 新任务永远优先
- ✅ 重试任务有冷却时间
- ✅ 不会因为大面积失败阻塞新任务

---

## 八、技术选型

### 8.1 技术栈

| 组件 | 选择 | 说明 |
|------|------|------|
| **语言** | Java 17 | 稳定可靠 |
| **框架** | Spring Boot 3.2 | 生态成熟 |
| **ORM** | MyBatis-Plus | 提高开发效率 |
| **数据库** | MySQL 8.0 | 可靠持久化 |
| **HTTP客户端** | RestTemplate | 同步，够用 |

### 8.2 为什么这样设计

| 问题 | 回答 |
|------|------|
| 为什么不用消息队列？ | MySQL 作为消息源，足够且简单 |
| 为什么新任务优先？ | 保证业务系统请求不被阻塞 |
| 为什么重试要冷却？ | 避免大量重试请求压垮外部系统 |

---

## 九、数据模型

### 9.1 通知任务表

```sql
CREATE TABLE notification_task (
    id VARCHAR(36) PRIMARY KEY,
    target VARCHAR(50) NOT NULL,
    payload JSON NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INT DEFAULT 0,
    max_retries INT DEFAULT 5,
    error_message TEXT,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    delivered_at DATETIME,
    INDEX idx_status (status),
    INDEX idx_retry_status (retry_count, status)
);
```

### 9.2 状态说明

| 状态 | 说明 |
|------|------|
| PENDING | 待处理，等待分配线程拉取 |
| SUCCESS | 投递成功 |
| FAILED | 超过最大重试次数 |

---

## 十、总结

### 10.1 核心设计

> **MySQL 作为消息源 + 新任务/重试任务分离 + 工作线程池统一消费**

### 10.2 优势

1. ✅ **可靠**：任务持久化在 MySQL，不会丢失
2. ✅ **隔离**：新任务和重试任务完全分离
3. ✅ **可控**：重试冷却时间可配置
4. ✅ **简单**：不需要复杂的消息队列

### 10.3 适用场景

- 通知量：< 10000/分钟
- 团队规模：小型
- 运维能力：有限
