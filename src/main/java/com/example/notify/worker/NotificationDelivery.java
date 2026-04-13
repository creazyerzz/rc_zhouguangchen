package com.example.notify.worker;

import com.alibaba.fastjson2.JSON;
import com.example.notify.entity.NotificationTask;
import com.example.notify.entity.TaskStatus;
import com.example.notify.entity.TargetConfig;
import com.example.notify.mapper.NotificationTaskMapper;
import com.example.notify.mapper.TargetConfigMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Service
public class NotificationDelivery {
    
    private final NotificationTaskMapper taskMapper;
    private final TargetConfigMapper configMapper;
    private final RestTemplate restTemplate;
    
    public NotificationDelivery(
            NotificationTaskMapper taskMapper,
            TargetConfigMapper configMapper) {
        this.taskMapper = taskMapper;
        this.configMapper = configMapper;
        this.restTemplate = new RestTemplate();
    }
    
    public void deliver(NotificationTask task) {
        try {
            TargetConfig config = configMapper.selectByTarget(task.getTarget());
            
            if (config == null) {
                handleFailure(task, "Target not found: " + task.getTarget());
                return;
            }
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            if (config.getHeaders() != null && !config.getHeaders().isEmpty()) {
                Map<String, String> headerMap = JSON.parseObject(config.getHeaders(), Map.class);
                headerMap.forEach(headers::set);
            }
            
            HttpEntity<String> entity = new HttpEntity<>(task.getPayload(), headers);
            restTemplate.postForEntity(config.getUrl(), entity, String.class);
            
            handleSuccess(task);
            
        } catch (Exception e) {
            log.error("Deliver task failed: taskId={}, error={}", task.getId(), e.getMessage());
            handleFailure(task, e.getMessage());
        }
    }
    
    private void handleSuccess(NotificationTask task) {
        taskMapper.updateStatus(task.getId(), TaskStatus.SUCCESS);
        log.info("Task delivered successfully: taskId={}", task.getId());
    }
    
    private void handleFailure(NotificationTask task, String errorMessage) {
        int currentRetry = task.getRetryCount() != null ? task.getRetryCount() : 0;
        int maxRetries = task.getMaxRetries() != null ? task.getMaxRetries() : 5;
        
        if (currentRetry < maxRetries) {
            int newRetryCount = currentRetry + 1;
            taskMapper.updateRetryCount(task.getId(), newRetryCount, errorMessage);
            log.info("Task will retry: taskId={}, retryCount={}", task.getId(), newRetryCount);
        } else {
            taskMapper.updateStatusWithError(task.getId(), TaskStatus.FAILED, 
                    "Max retries exceeded. Last error: " + errorMessage);
            log.warn("Task failed after max retries: taskId={}", task.getId());
        }
    }
}
