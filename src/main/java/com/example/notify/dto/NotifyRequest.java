package com.example.notify.dto;

import lombok.Data;

import java.util.Map;

@Data
public class NotifyRequest {
    
    private String target;
    
    private Map<String, Object> payload;
}
