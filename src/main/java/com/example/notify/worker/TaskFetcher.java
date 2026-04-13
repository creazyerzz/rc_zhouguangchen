package com.example.notify.worker;

import com.example.notify.entity.NotificationTask;
import com.example.notify.mapper.NotificationTaskMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class TaskFetcher {
    
    private final NotificationTaskMapper taskMapper;
    private final NotificationDelivery delivery;
    
    private final AtomicBoolean running = new AtomicBoolean(true);
    
    private final ExecutorService newTaskExecutor;
    private final ExecutorService workerExecutor;
    
    private final BlockingQueue<NotificationTask> newTaskQueue;
    private final BlockingQueue<NotificationTask> retryTaskQueue;
    
    private ScheduledExecutorService retryScheduler;
    
    @Value("${notify.worker.thread-count:3}")
    private int threadCount;
    
    @Value("${notify.worker.fetch-batch-size:50}")
    private int fetchBatchSize;
    
    @Value("${notify.worker.queue-capacity:1000}")
    private int queueCapacity;
    
    @Value("${notify.worker.retry-delay-seconds:60}")
    private int retryDelaySeconds;
    
    public TaskFetcher(
            NotificationTaskMapper taskMapper,
            NotificationDelivery delivery) {
        this.taskMapper = taskMapper;
        this.delivery = delivery;
        
        this.newTaskQueue = new LinkedBlockingQueue<>(queueCapacity);
        this.retryTaskQueue = new LinkedBlockingQueue<>(queueCapacity);
        
        this.newTaskExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "new-task-fetcher");
            t.setDaemon(true);
            return t;
        });
        
        this.workerExecutor = Executors.newFixedThreadPool(threadCount);
    }
    
    @PostConstruct
    public void start() {
        newTaskExecutor.submit(this::fetchNewTasksLoop);
        
        retryScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "retry-scheduler");
            t.setDaemon(true);
            return t;
        });
        retryScheduler.scheduleAtFixedRate(
            this::fetchRetryTasks, 
            retryDelaySeconds, 
            retryDelaySeconds, 
            TimeUnit.SECONDS
        );
        
        for (int i = 0; i < threadCount; i++) {
            final int workerId = i;
            workerExecutor.submit(() -> workLoop(workerId));
        }
        
        log.info("TaskFetcher started: {} workers, batch size: {}, retry delay: {}s",
                threadCount, fetchBatchSize, retryDelaySeconds);
    }
    
    @PreDestroy
    public void stop() {
        running.set(false);
        newTaskExecutor.shutdown();
        retryScheduler.shutdown();
        workerExecutor.shutdown();
        
        try {
            if (!newTaskExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                newTaskExecutor.shutdownNow();
            }
            if (!retryScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                retryScheduler.shutdownNow();
            }
            if (!workerExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                workerExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            newTaskExecutor.shutdownNow();
            retryScheduler.shutdownNow();
            workerExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        log.info("TaskFetcher stopped");
    }
    
    private void fetchNewTasksLoop() {
        Thread.currentThread().setName("new-task-fetcher");
        
        while (running.get()) {
            try {
                List<NotificationTask> tasks = taskMapper.selectNewTasks(fetchBatchSize);
                
                if (tasks.isEmpty()) {
                    Thread.sleep(100);
                    continue;
                }
                
                for (NotificationTask task : tasks) {
                    boolean offered = newTaskQueue.offer(task, 1, TimeUnit.SECONDS);
                    if (!offered) {
                        log.warn("New task queue is full");
                        break;
                    }
                }
                
                log.debug("Fetched {} new tasks", tasks.size());
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Failed to fetch new tasks", e);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }
    
    private void fetchRetryTasks() {
        try {
            List<NotificationTask> tasks = taskMapper.selectRetryTasks(
                retryDelaySeconds, 
                fetchBatchSize
            );
            
            if (tasks.isEmpty()) {
                return;
            }
            
            for (NotificationTask task : tasks) {
                boolean offered = retryTaskQueue.offer(task, 1, TimeUnit.SECONDS);
                if (!offered) {
                    log.warn("Retry task queue is full");
                    break;
                }
            }
            
            log.debug("Fetched {} retry tasks", tasks.size());
            
        } catch (Exception e) {
            log.error("Failed to fetch retry tasks", e);
        }
    }
    
    private void workLoop(int workerId) {
        Thread.currentThread().setName("worker-" + workerId);
        
        while (running.get()) {
            try {
                NotificationTask task = pollNextTask();
                
                if (task != null) {
                    try {
                        delivery.deliver(task);
                    } catch (Exception e) {
                        log.error("Worker {} failed to deliver task: {}", workerId, task.getId(), e);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        log.info("Worker {} stopped", workerId);
    }
    
    private NotificationTask pollNextTask() throws InterruptedException {
        NotificationTask task = newTaskQueue.poll(100, TimeUnit.MILLISECONDS);
        
        if (task != null) {
            return task;
        }
        
        return retryTaskQueue.poll(100, TimeUnit.MILLISECONDS);
    }
}
