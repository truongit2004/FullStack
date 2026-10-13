package com.example.ShippingService.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

@FeignClient(name = "order-service", path = "/api/orders")
// Ngắt mạch: service này lỗi liên tục thì tạm ngừng gọi, trả lỗi ngay thay vì chờ hết timeout
@CircuitBreaker(name = "orderService")
public interface OrderClient {

    @PutMapping("/{id}/ship")
    Object shipOrder(@PathVariable("id") String id);

    @PutMapping("/{id}/deliver")
    Object deliverOrder(@PathVariable("id") String id);

    @PutMapping("/{id}/cancel")
    Object cancelOrder(@PathVariable("id") String id);

    @PutMapping("/{id}/return")
    Object returnOrder(@PathVariable("id") String id);

    @Retry(name = "orderService")
    @org.springframework.web.bind.annotation.GetMapping("/{id}")
    com.example.ShippingService.dto.ExternalOrderResponseDTO getOrderById(@PathVariable("id") String id);
}
