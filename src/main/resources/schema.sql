CREATE DATABASE IF NOT EXISTS notify_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE notify_db;

CREATE TABLE IF NOT EXISTS notification_task (
    id VARCHAR(36) PRIMARY KEY COMMENT '任务ID',
    target VARCHAR(50) NOT NULL COMMENT '目标系统标识',
    payload JSON NOT NULL COMMENT '业务数据',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '任务状态',
    retry_count INT DEFAULT 0 COMMENT '当前重试次数',
    max_retries INT DEFAULT 5 COMMENT '最大重试次数',
    error_message TEXT COMMENT '错误信息',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    delivered_at DATETIME COMMENT '投递成功时间',

    INDEX idx_status (status),
    INDEX idx_target (target),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知任务表';

CREATE TABLE IF NOT EXISTS target_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    target VARCHAR(50) NOT NULL UNIQUE COMMENT '目标系统标识',
    name VARCHAR(100) NOT NULL COMMENT '目标系统名称',
    url VARCHAR(512) NOT NULL COMMENT 'API地址',
    headers JSON COMMENT '请求头配置',
    timeout INT DEFAULT 10 COMMENT '超时时间（秒）',
    max_retries INT DEFAULT 5 COMMENT '最大重试次数',
    enabled BOOLEAN DEFAULT TRUE COMMENT '是否启用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    INDEX idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='目标系统配置表';

INSERT INTO target_config (target, name, url, headers, timeout, max_retries, enabled) VALUES
('advertisement', '广告系统', 'https://ads.example.com/api/notify', '{"Content-Type": "application/json"}', 10, 5, TRUE),
('crm', 'CRM系统', 'https://crm.example.com/webhook', '{"Content-Type": "application/json"}', 10, 5, TRUE),
('inventory', '库存系统', 'https://inventory.example.com/api/update', '{"Content-Type": "application/json"}', 10, 5, TRUE);
