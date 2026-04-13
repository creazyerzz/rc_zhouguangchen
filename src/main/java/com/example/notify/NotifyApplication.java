package com.example.notify;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.example.notify.mapper")
public class NotifyApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(NotifyApplication.class, args);
    }
}
