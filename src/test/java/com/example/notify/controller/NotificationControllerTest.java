package com.example.notify.controller;

import com.example.notify.dto.NotificationVO;
import com.example.notify.dto.NotifyRequest;
import com.example.notify.entity.TaskStatus;
import com.example.notify.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NotificationController.class)
class NotificationControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private NotificationService notificationService;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Test
    void testCreateNotification_Success() throws Exception {
        NotifyRequest request = new NotifyRequest();
        request.setTarget("advertisement");
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("event", "user_registered");
        payload.put("user_id", "12345");
        request.setPayload(payload);
        
        when(notificationService.create(any(NotifyRequest.class)))
                .thenReturn("test-task-id-12345");
        
        mockMvc.perform(post("/api/notify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data").value("test-task-id-12345"));
    }
    
    @Test
    void testCreateNotification_MissingTarget() throws Exception {
        NotifyRequest request = new NotifyRequest();
        request.setPayload(Map.of("event", "test"));
        
        mockMvc.perform(post("/api/notify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Target is required"));
    }
    
    @Test
    void testCreateNotification_EmptyTarget() throws Exception {
        NotifyRequest request = new NotifyRequest();
        request.setTarget("");
        request.setPayload(Map.of("event", "test"));
        
        mockMvc.perform(post("/api/notify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Target is required"));
    }
    
    @Test
    void testCreateNotification_MissingPayload() throws Exception {
        NotifyRequest request = new NotifyRequest();
        request.setTarget("advertisement");
        
        mockMvc.perform(post("/api/notify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Payload is required"));
    }
    
    @Test
    void testCreateNotification_InvalidTarget() throws Exception {
        NotifyRequest request = new NotifyRequest();
        request.setTarget("unknown");
        request.setPayload(Map.of("event", "test"));
        
        when(notificationService.create(any(NotifyRequest.class)))
                .thenThrow(new IllegalArgumentException("Unknown target: unknown"));
        
        mockMvc.perform(post("/api/notify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Unknown target: unknown"));
    }
    
    @Test
    void testGetNotification_Found() throws Exception {
        NotificationVO vo = new NotificationVO();
        vo.setTaskId("test-task-id");
        vo.setTarget("advertisement");
        vo.setStatus(TaskStatus.SUCCESS);
        vo.setRetryCount(0);
        vo.setMaxRetries(5);
        vo.setCreatedAt(LocalDateTime.now());
        vo.setDeliveredAt(LocalDateTime.now());
        
        when(notificationService.getById("test-task-id")).thenReturn(vo);
        
        mockMvc.perform(get("/api/notify/test-task-id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.taskId").value("test-task-id"))
                .andExpect(jsonPath("$.data.target").value("advertisement"))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.retryCount").value(0))
                .andExpect(jsonPath("$.data.maxRetries").value(5));
    }
    
    @Test
    void testGetNotification_NotFound() throws Exception {
        when(notificationService.getById("non-existent")).thenReturn(null);
        
        mockMvc.perform(get("/api/notify/non-existent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("Task not found"));
    }
}
