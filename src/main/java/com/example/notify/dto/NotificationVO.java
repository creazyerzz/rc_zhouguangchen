package com.example.notify.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NotificationVO {
    
    private String taskId;
    
    private String target;
    
    private String status;
    
    private Integer retryCount;
    
    private Integer maxRetries;
    
    private String errorMessage;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
    
    private LocalDateTime deliveredAt;
}
