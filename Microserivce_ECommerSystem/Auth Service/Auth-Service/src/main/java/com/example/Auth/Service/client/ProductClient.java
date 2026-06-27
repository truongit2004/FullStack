package com.example.Auth.Service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@FeignClient(name = "PRODUCT-SERVICE", url = "http://localhost:8082", configuration = com.example.Auth.Service.config.FeignConfig.class)
public interface ProductClient {

    @GetMapping("/api/products/filter")
    Map<String, Object> filterProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    );

    @GetMapping("/api/products/{id}")
    Object getProductById(@PathVariable("id") String id);

    @GetMapping("/api/products/all")
    List<Object> getAllProducts();
}
