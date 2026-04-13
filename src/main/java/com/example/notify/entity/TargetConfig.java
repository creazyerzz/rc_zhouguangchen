package com.example.notify.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("target_config")
public class TargetConfig {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String target;
    
    private String name;
    
    private String url;
    
    private String headers;
    
    private Integer timeout;
    
    private Integer maxRetries;
    
    private Boolean enabled;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
}
