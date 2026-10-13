package com.example.OrderService.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

import com.example.OrderService.dto.InventoryResponseDTO;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

@FeignClient(name = "inventory-service", path = "/api/inventory")
// Ngắt mạch: service này lỗi liên tục thì tạm ngừng gọi, trả lỗi ngay thay vì chờ hết timeout
@CircuitBreaker(name = "inventoryService")
public interface InventoryClient {

    @Retry(name = "inventoryService")
    @GetMapping("/{productId}")
    InventoryResponseDTO getStock(@PathVariable("productId") String productId);

    @PostMapping("/decrease")
    void decreaseStock(@RequestParam("productId") String productId, @RequestParam("quantity") Integer quantity);

    @PostMapping("/increase")
    void increaseStock(@RequestParam("productId") String productId, @RequestParam("quantity") Integer quantity);

    @PostMapping("/decrease-bulk")
    void decreaseStockBulk(@RequestBody Map<String, Integer> items);

    @PostMapping("/increase-bulk")
    void increaseStockBulk(@RequestBody Map<String, Integer> items);
}
