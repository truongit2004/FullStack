package com.example.Auth.Service.client;

import com.example.Auth.Service.dto.PasswordResetEmailRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;

@FeignClient(name = "NOTIFICATION-SERVICE", url = "http://localhost:8090")
// Ngắt mạch: service này lỗi liên tục thì tạm ngừng gọi, trả lỗi ngay thay vì chờ hết timeout
@CircuitBreaker(name = "notificationService")
public interface NotificationClient {

    @PostMapping("/api/notifications/send-password-reset")
    void sendPasswordReset(@RequestBody PasswordResetEmailRequest request);
}
