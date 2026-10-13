package com.example.ReviewService.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

@FeignClient(name = "ORDER-SERVICE")
// Ngắt mạch: service này lỗi liên tục thì tạm ngừng gọi, trả lỗi ngay thay vì chờ hết timeout
@CircuitBreaker(name = "orderService")
public interface OrderClient {

    @Retry(name = "orderService")
    @GetMapping("/api/orders/check-purchased")
    Boolean checkPurchased(@RequestParam("userId") String userId, @RequestParam("productId") String productId);
}
