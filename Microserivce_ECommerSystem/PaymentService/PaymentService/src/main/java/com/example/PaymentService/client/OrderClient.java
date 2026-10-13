package com.example.PaymentService.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

@FeignClient(name = "order-service")
// Ngắt mạch: service này lỗi liên tục thì tạm ngừng gọi, trả lỗi ngay thay vì chờ hết timeout
@CircuitBreaker(name = "orderService")
public interface OrderClient {

    @Retry(name = "orderService")
    @GetMapping("/api/orders/{id}")
    Object getOrderById(@PathVariable("id") String id);

    @Retry(name = "orderService")
    @PostMapping("/api/orders/{id}/paid")
    void markOrderAsPaid(@PathVariable("id") String id);
}
