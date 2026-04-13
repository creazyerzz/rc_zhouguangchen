# API 通知系统

企业内部通知投递服务，实现可靠的通知投递功能。

## 功能特性

- ✅ 统一 API 入口
- ✅ 可靠持久化（MySQL）
- ✅ 异步投递（新任务/重试任务分离）
- ✅ 失败重试（冷却机制）
- ✅ 状态追踪

## 技术栈

- Java 17
- Spring Boot 3.2
- MyBatis-Plus 3.5
- MySQL 8.0

## 快速开始

### 1. 环境要求

- JDK 17+
- Maven 3.6+
- MySQL 8.0+

### 2. 数据库初始化

```bash
mysql -u root -p < src/main/resources/schema.sql
```

### 3. 修改配置

编辑 `src/main/resources/application.yml`，修改数据库连接信息：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/notify_db
    username: your_username
    password: your_password
```

### 4. 编译运行

```bash
mvn clean package -DskipTests
java -jar target/api-notify-server-1.0.0.jar
```

服务启动后监听 `http://localhost:8080`

## API 接口

### 提交通知

```bash
POST /api/notify
Content-Type: application/json

{
  "target": "advertisement",
  "payload": {
    "event": "user_registered",
    "user_id": "12345"
  }
}
```

**响应示例**：

```json
{
  "code": 0,
  "message": "success",
  "data": "550e8400-e29b-41d4-a716-446655440000"
}
```

### 查询状态

```bash
GET /api/notify/{taskId}
```

**响应示例**：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "taskId": "550e8400-e29b-41d4-a716-446655440000",
    "target": "advertisement",
    "status": "SUCCESS",
    "retryCount": 0,
    "createdAt": "2024-01-01T10:00:00",
    "deliveredAt": "2024-01-01T10:00:01"
  }
}
```

## 任务状态说明

| 状态 | 说明 |
|------|------|
| PENDING | 待处理，等待分配线程拉取 |
| SUCCESS | 投递成功 |
| FAILED | 超过最大重试次数 |

## 重试策略

- 最大重试次数：5 次（可配置）
- 重试冷却时间：60 秒（可配置）
- 新任务和重试任务完全分离
- 新任务（retry_count=0）优先处理
- 重试任务冷却后再被拉取

## 配置参数

```yaml
notify:
  worker:
    thread-count: 3              # 工作线程数
    fetch-batch-size: 50       # 每次拉取数量
    queue-capacity: 1000        # 内存队列容量
    retry-delay-seconds: 60     # 重试冷却时间（秒）
```

## 项目结构

```
api-notify-server/
├── src/main/java/com/example/notify/
│   ├── NotifyApplication.java           # 启动类
│   ├── controller/                     # API 控制器
│   │   └── NotificationController.java
│   ├── service/                        # 业务逻辑
│   │   └── NotificationService.java
│   ├── worker/                         # 投递工作线程
│   │   ├── TaskFetcher.java           # 分配线程
│   │   └── NotificationDelivery.java  # 投递服务
│   ├── mapper/                        # 数据访问
│   │   ├── NotificationTaskMapper.java
│   │   └── TargetConfigMapper.java
│   ├── entity/                        # 实体类
│   │   ├── NotificationTask.java
│   │   ├── TargetConfig.java
│   │   └── TaskStatus.java
│   └── dto/                           # 数据传输对象
│       ├── NotifyRequest.java
│       ├── NotificationVO.java
│       └── Result.java
├── src/main/resources/
│   ├── application.yml                # 配置文件
│   └── schema.sql                     # 数据库脚本
├── src/test/                          # 测试
└── pom.xml                            # Maven 配置
```

## 设计文档

详细的设计说明请查看 [DESIGN.md](DESIGN.md)

## AI 使用说明

AI 在本项目中的使用说明请查看 [AI_USAGE.md](AI_USAGE.md)

## License

MIT
