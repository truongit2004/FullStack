package com.example.OrderService.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.OrderService.dto.InventoryResponseDTO;

@FeignClient(name = "inventory-service", path = "/api/inventory")
public interface InventoryClient {

    @GetMapping("/{productId}")
    InventoryResponseDTO getStock(@PathVariable("productId") String productId);

    @PostMapping("/decrease")
    void decreaseStock(@RequestParam("productId") String productId, @RequestParam("quantity") Integer quantity);

    @PostMapping("/increase")
    void increaseStock(@RequestParam("productId") String productId, @RequestParam("quantity") Integer quantity);

    @PostMapping("/decrease-bulk")
    void decreaseStockBulk(@org.springframework.web.bind.annotation.RequestBody java.util.Map<String, Integer> items);

    @PostMapping("/increase-bulk")
    void increaseStockBulk(@org.springframework.web.bind.annotation.RequestBody java.util.Map<String, Integer> items);
}
