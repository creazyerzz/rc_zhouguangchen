package com.example.notify.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("notification_task")
public class NotificationTask {
    
    @TableId(type = IdType.INPUT)
    private String id;
    
    private String target;
    
    private String payload;
    
    private String status;
    
    private Integer retryCount;
    
    private Integer maxRetries;
    
    private String errorMessage;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
    
    private LocalDateTime deliveredAt;
}
