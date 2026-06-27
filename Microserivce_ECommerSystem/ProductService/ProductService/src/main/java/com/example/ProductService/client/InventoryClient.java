package com.example.ProductService.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "inventory-service", path = "/api/inventory")
public interface InventoryClient {

    @GetMapping("/{productId}")
    Object getStock(@PathVariable("productId") String productId);

    @PostMapping("/decrease")
    void decreaseStock(@RequestParam("productId") String productId, @RequestParam("quantity") Integer quantity);

    @PostMapping("/increase")
    void increaseStock(@RequestParam("productId") String productId, @RequestParam("quantity") Integer quantity);

    @PostMapping("/create")
    void createInventory(@RequestParam("productId") String productId, @RequestParam(value = "initialStock", required = false) Integer initialStock);
}
