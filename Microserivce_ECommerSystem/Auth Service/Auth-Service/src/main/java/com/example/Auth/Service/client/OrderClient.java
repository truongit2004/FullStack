package com.example.Auth.Service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

@FeignClient(name = "ORDER-SERVICE", url = "http://localhost:8084")
// Ngắt mạch: service này lỗi liên tục thì tạm ngừng gọi, trả lỗi ngay thay vì chờ hết timeout
@CircuitBreaker(name = "orderService")
public interface OrderClient {

    @Retry(name = "orderService")
    @GetMapping("/api/orders")
    Map<String, Object> getAllOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    );

    @Retry(name = "orderService")
    @GetMapping("/api/orders/{id}")
    Object getOrderById(@PathVariable("id") String id);

    @Retry(name = "orderService")
    @GetMapping("/api/orders/user/{userId}")
    Map<String, Object> getOrdersByUser(
            @PathVariable("userId") String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    );
}
