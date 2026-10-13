package com.example.OrderService.client;

import com.example.OrderService.dto.ProductResponseDTO;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

@FeignClient(name = "product-service", url = "${product.service.url}")
// Ngắt mạch: service này lỗi liên tục thì tạm ngừng gọi, trả lỗi ngay thay vì chờ hết timeout
@CircuitBreaker(name = "productService")
public interface ProductClient {

    @Retry(name = "productService")
    @GetMapping("/api/products/{id}")
    ProductResponseDTO getProductById(@PathVariable("id") String productId);

    @PutMapping("/api/products/{id}/stock/decrease")
    void decreaseStock(@PathVariable("id") String productId,
                       @RequestParam("quantity") Integer quantity);

    @PutMapping("/api/products/{id}/stock/increase")
    void increaseStock(@PathVariable("id") String productId,
                       @RequestParam("quantity") Integer quantity);
}