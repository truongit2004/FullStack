package com.example.OrderService.client;

import com.example.OrderService.dto.PointRedeemRequestDTO;
import com.example.OrderService.dto.PointRedeemResponseDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

@FeignClient(name = "point-service", path = "/api/points")
// Ngắt mạch: service này lỗi liên tục thì tạm ngừng gọi, trả lỗi ngay thay vì chờ hết timeout
@CircuitBreaker(name = "pointService")
public interface PointClient {

    @Retry(name = "pointService")
    @PostMapping("/redeem")
    PointRedeemResponseDTO redeem(@RequestBody PointRedeemRequestDTO request);

    @Retry(name = "pointService")
    @PostMapping("/{orderId}/reverse")
    void reverse(@PathVariable("orderId") String orderId);
}
