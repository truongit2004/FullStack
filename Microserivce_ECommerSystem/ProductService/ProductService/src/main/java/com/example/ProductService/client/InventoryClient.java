package com.example.ProductService.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

@FeignClient(name = "inventory-service", path = "/api/inventory")
// Ngắt mạch: service này lỗi liên tục thì tạm ngừng gọi, trả lỗi ngay thay vì chờ hết timeout
@CircuitBreaker(name = "inventoryService")
public interface InventoryClient {

    @Retry(name = "inventoryService")
    @GetMapping("/{productId}")
    Object getStock(@PathVariable("productId") String productId);

    @PostMapping("/decrease")
    void decreaseStock(@RequestParam("productId") String productId, @RequestParam("quantity") Integer quantity);

    @PostMapping("/increase")
    void increaseStock(@RequestParam("productId") String productId, @RequestParam("quantity") Integer quantity);

    @PostMapping("/create")
    void createInventory(@RequestParam("productId") String productId, @RequestParam(value = "initialStock", required = false) Integer initialStock);
}
