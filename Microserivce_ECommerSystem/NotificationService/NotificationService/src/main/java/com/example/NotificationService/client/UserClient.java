package com.example.NotificationService.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "user-service") // Hoặc auth-service tùy hệ thống bạn đặt tên
public interface UserClient {
    @GetMapping("/api/users/{id}")
    Object getUserById(@PathVariable("id") String id);
}
