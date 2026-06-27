package com.example.OrderService.client;

import com.example.OrderService.dto.ProductResponseDTO;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "product-service", url = "${product.service.url}")
public interface ProductClient {

    @GetMapping("/api/products/{id}")
    ProductResponseDTO getProductById(@PathVariable("id") String productId);

    @PutMapping("/api/products/{id}/stock/decrease")
    void decreaseStock(@PathVariable("id") String productId,
                       @RequestParam("quantity") Integer quantity);

    @PutMapping("/api/products/{id}/stock/increase")
    void increaseStock(@PathVariable("id") String productId,
                       @RequestParam("quantity") Integer quantity);
}