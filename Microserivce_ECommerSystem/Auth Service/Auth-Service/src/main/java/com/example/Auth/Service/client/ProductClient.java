package com.example.Auth.Service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

@FeignClient(name = "PRODUCT-SERVICE", url = "http://localhost:8082", configuration = com.example.Auth.Service.config.FeignConfig.class)
// Ngắt mạch: service này lỗi liên tục thì tạm ngừng gọi, trả lỗi ngay thay vì chờ hết timeout
@CircuitBreaker(name = "productService")
public interface ProductClient {

    @Retry(name = "productService")
    @GetMapping("/api/products/filter")
    Map<String, Object> filterProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    );

    @Retry(name = "productService")
    @GetMapping("/api/products/{id}")
    Object getProductById(@PathVariable("id") String id);

    @Retry(name = "productService")
    @GetMapping("/api/products/all")
    List<Object> getAllProducts();
}
