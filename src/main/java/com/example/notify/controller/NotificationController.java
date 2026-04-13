package com.example.notify.controller;

import com.example.notify.dto.NotificationVO;
import com.example.notify.dto.NotifyRequest;
import com.example.notify.dto.Result;
import com.example.notify.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api")
public class NotificationController {
    
    private final NotificationService notificationService;
    
    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }
    
    @PostMapping("/notify")
    public Result<String> createNotification(@RequestBody NotifyRequest request) {
        try {
            if (request.getTarget() == null || request.getTarget().isEmpty()) {
                return Result.error(400, "Target is required");
            }
            
            if (request.getPayload() == null || request.getPayload().isEmpty()) {
                return Result.error(400, "Payload is required");
            }
            
            String taskId = notificationService.create(request);
            
            return Result.success(taskId);
            
        } catch (IllegalArgumentException e) {
            log.warn("Invalid request: {}", e.getMessage());
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("Create notification failed", e);
            return Result.error(500, "Internal server error");
        }
    }
    
    @GetMapping("/notify/{taskId}")
    public Result<NotificationVO> getNotification(@PathVariable String taskId) {
        try {
            NotificationVO vo = notificationService.getById(taskId);
            
            if (vo == null) {
                return Result.error(404, "Task not found");
            }
            
            return Result.success(vo);
            
        } catch (Exception e) {
            log.error("Get notification failed: taskId={}", taskId, e);
            return Result.error(500, "Internal server error");
        }
    }
}
