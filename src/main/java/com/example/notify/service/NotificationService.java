package com.example.notify.service;

import com.alibaba.fastjson2.JSON;
import com.example.notify.dto.NotificationVO;
import com.example.notify.dto.NotifyRequest;
import com.example.notify.entity.NotificationTask;
import com.example.notify.entity.TaskStatus;
import com.example.notify.entity.TargetConfig;
import com.example.notify.mapper.NotificationTaskMapper;
import com.example.notify.mapper.TargetConfigMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
public class NotificationService {
    
    private final NotificationTaskMapper taskMapper;
    private final TargetConfigMapper configMapper;
    
    public NotificationService(
            NotificationTaskMapper taskMapper,
            TargetConfigMapper configMapper) {
        this.taskMapper = taskMapper;
        this.configMapper = configMapper;
    }
    
    public String create(NotifyRequest request) {
        TargetConfig config = configMapper.selectByTarget(request.getTarget());
        
        if (config == null) {
            throw new IllegalArgumentException("Unknown target: " + request.getTarget());
        }
        
        if (!config.getEnabled()) {
            throw new IllegalArgumentException("Target is disabled: " + request.getTarget());
        }
        
        NotificationTask task = new NotificationTask();
        task.setId(java.util.UUID.randomUUID().toString());
        task.setTarget(request.getTarget());
        task.setPayload(JSON.toJSONString(request.getPayload()));
        task.setStatus(TaskStatus.PENDING);
        task.setRetryCount(0);
        task.setMaxRetries(config.getMaxRetries() != null ? config.getMaxRetries() : 5);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        
        taskMapper.insertTask(task);
        
        log.info("Notification task created: taskId={}, target={}", task.getId(), task.getTarget());
        
        return task.getId();
    }
    
    public NotificationVO getById(String taskId) {
        NotificationTask task = taskMapper.selectById(taskId);
        
        if (task == null) {
            return null;
        }
        
        NotificationVO vo = new NotificationVO();
        vo.setTaskId(task.getId());
        vo.setTarget(task.getTarget());
        vo.setStatus(task.getStatus());
        vo.setRetryCount(task.getRetryCount());
        vo.setMaxRetries(task.getMaxRetries());
        vo.setErrorMessage(task.getErrorMessage());
        vo.setCreatedAt(task.getCreatedAt());
        vo.setUpdatedAt(task.getUpdatedAt());
        vo.setDeliveredAt(task.getDeliveredAt());
        
        return vo;
    }
}
