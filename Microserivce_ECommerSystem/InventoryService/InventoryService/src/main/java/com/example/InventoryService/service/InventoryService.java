package com.example.InventoryService.service;

import com.example.InventoryService.dto.InventoryResponseDTO;

public interface InventoryService {
    InventoryResponseDTO getStock(String productId);
    void decreaseStock(String productId, Integer quantity);
    void decreaseStockBulk(java.util.Map<String, Integer> items);
    void increaseStock(String productId, Integer quantity);
    void increaseStockBulk(java.util.Map<String, Integer> items);
    void createInventory(String productId, Integer initialStock);
}
