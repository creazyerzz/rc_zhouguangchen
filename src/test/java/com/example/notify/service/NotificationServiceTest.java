package com.example.notify.service;

import com.example.notify.dto.NotificationVO;
import com.example.notify.dto.NotifyRequest;
import com.example.notify.entity.NotificationTask;
import com.example.notify.entity.TaskStatus;
import com.example.notify.entity.TargetConfig;
import com.example.notify.mapper.NotificationTaskMapper;
import com.example.notify.mapper.TargetConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {
    
    @Mock
    private NotificationTaskMapper taskMapper;
    
    @Mock
    private TargetConfigMapper configMapper;
    
    private NotificationService notificationService;
    
    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(taskMapper, configMapper);
    }
    
    @Test
    void testCreate_Success() {
        NotifyRequest request = new NotifyRequest();
        request.setTarget("advertisement");
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("event", "user_registered");
        payload.put("user_id", "12345");
        request.setPayload(payload);
        
        TargetConfig config = new TargetConfig();
        config.setTarget("advertisement");
        config.setEnabled(true);
        config.setMaxRetries(5);
        
        when(configMapper.selectByTarget("advertisement")).thenReturn(config);
        when(taskMapper.insertTask(any(NotificationTask.class))).thenReturn(1);
        
        String taskId = notificationService.create(request);
        
        assertNotNull(taskId);
        assertFalse(taskId.isEmpty());
        
        ArgumentCaptor<NotificationTask> taskCaptor = ArgumentCaptor.forClass(NotificationTask.class);
        verify(taskMapper).insertTask(taskCaptor.capture());
        
        NotificationTask savedTask = taskCaptor.getValue();
        assertEquals("advertisement", savedTask.getTarget());
        assertEquals(TaskStatus.PENDING, savedTask.getStatus());
        assertEquals(0, savedTask.getRetryCount());
        assertEquals(5, savedTask.getMaxRetries());
    }
    
    @Test
    void testCreate_TargetNotFound() {
        NotifyRequest request = new NotifyRequest();
        request.setTarget("unknown");
        
        when(configMapper.selectByTarget("unknown")).thenReturn(null);
        
        assertThrows(IllegalArgumentException.class, () -> {
            notificationService.create(request);
        });
        
        verify(taskMapper, never()).insertTask(any());
    }
    
    @Test
    void testCreate_TargetDisabled() {
        NotifyRequest request = new NotifyRequest();
        request.setTarget("disabled_target");
        
        TargetConfig config = new TargetConfig();
        config.setTarget("disabled_target");
        config.setEnabled(false);
        
        when(configMapper.selectByTarget("disabled_target")).thenReturn(config);
        
        assertThrows(IllegalArgumentException.class, () -> {
            notificationService.create(request);
        });
        
        verify(taskMapper, never()).insertTask(any());
    }
    
    @Test
    void testGetById_Found() {
        NotificationTask task = new NotificationTask();
        task.setId("test-task-id");
        task.setTarget("advertisement");
        task.setStatus(TaskStatus.SUCCESS);
        task.setRetryCount(0);
        task.setMaxRetries(5);
        task.setCreatedAt(LocalDateTime.now());
        task.setDeliveredAt(LocalDateTime.now());
        
        when(taskMapper.selectById("test-task-id")).thenReturn(task);
        
        NotificationVO result = notificationService.getById("test-task-id");
        
        assertNotNull(result);
        assertEquals("test-task-id", result.getTaskId());
        assertEquals("advertisement", result.getTarget());
        assertEquals(TaskStatus.SUCCESS, result.getStatus());
        assertEquals(0, result.getRetryCount());
        assertEquals(5, result.getMaxRetries());
    }
    
    @Test
    void testGetById_NotFound() {
        when(taskMapper.selectById("non-existent-id")).thenReturn(null);
        
        NotificationVO result = notificationService.getById("non-existent-id");
        
        assertNull(result);
    }
}
