package com.example.notify.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.notify.entity.NotificationTask;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface NotificationTaskMapper extends BaseMapper<NotificationTask> {
    
    @Insert("INSERT INTO notification_task " +
            "(id, target, payload, status, retry_count, max_retries, created_at, updated_at) " +
            "VALUES " +
            "(#{id}, #{target}, #{payload}, #{status}, #{retryCount}, #{maxRetries}, #{createdAt}, #{updatedAt})")
    @Options(useGeneratedKeys = false)
    int insertTask(NotificationTask task);
    
    @Update("UPDATE notification_task SET status = #{status}, updated_at = NOW() WHERE id = #{id}")
    int updateStatus(@Param("id") String id, @Param("status") String status);
    
    @Update("UPDATE notification_task SET status = #{status}, error_message = #{errorMessage}, " +
            "updated_at = NOW() WHERE id = #{id}")
    int updateStatusWithError(@Param("id") String id, @Param("status") String status, 
                              @Param("errorMessage") String errorMessage);
    
    @Update("UPDATE notification_task SET retry_count = #{retryCount}, " +
            "error_message = #{errorMessage}, updated_at = NOW() " +
            "WHERE id = #{id}")
    int updateRetryCount(@Param("id") String id, @Param("retryCount") Integer retryCount,
                         @Param("errorMessage") String errorMessage);
    
    @Select("<script>" +
            "SELECT * FROM notification_task " +
            "WHERE status = 'PENDING' " +
            "AND retry_count = 0 " +
            "ORDER BY created_at ASC " +
            "LIMIT #{limit}" +
            "</script>")
    List<NotificationTask> selectNewTasks(@Param("limit") int limit);
    
    @Select("<script>" +
            "SELECT * FROM notification_task " +
            "WHERE status = 'PENDING' " +
            "AND retry_count &gt; 0 " +
            "AND retry_count &lt; max_retries " +
            "AND updated_at &lt;= DATE_SUB(NOW(), INTERVAL #{delaySeconds} SECOND) " +
            "ORDER BY retry_count ASC, updated_at ASC " +
            "LIMIT #{limit}" +
            "</script>")
    List<NotificationTask> selectRetryTasks(@Param("delaySeconds") int delaySeconds, 
                                            @Param("limit") int limit);
}
