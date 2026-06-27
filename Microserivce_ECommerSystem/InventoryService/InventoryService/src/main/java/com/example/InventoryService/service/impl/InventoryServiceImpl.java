package com.example.InventoryService.service.impl;

import com.example.InventoryService.dto.InventoryResponseDTO;
import com.example.InventoryService.entity.InventoryEntity;
import com.example.InventoryService.exception.InsufficientStockException;
import com.example.InventoryService.repository.InventoryRepository;
import com.example.InventoryService.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;

    @Override
    public InventoryResponseDTO getStock(String productId) {
        return inventoryRepository.findByProductId(productId)
                .map(inv -> InventoryResponseDTO.builder()
                        .productId(inv.getProductId())
                        .stockQuantity(inv.getStockQuantity())
                        .build())
                .orElse(InventoryResponseDTO.builder().productId(productId).stockQuantity(0).build());
    }

    @Override
    @Transactional
    public void decreaseStock(String productId, Integer quantity) {
        log.info("Attempting to decrease stock for productId: {}, quantity: {}", productId, quantity);
        InventoryEntity inventory = inventoryRepository.findByProductIdWithLock(productId)
                .orElseThrow(() -> new InsufficientStockException("Không tìm thấy thông tin tồn kho cho sản phẩm: " + productId));
        
        if (inventory.getStockQuantity() < quantity) {
            log.warn("Stock depletion attempt: productId={}, current={}, requested={}",
                    productId, inventory.getStockQuantity(), quantity);
            throw new InsufficientStockException("Sản phẩm không đủ tồn kho (Cần: " + quantity + ", Hiện có: " + inventory.getStockQuantity() + ")");
        }
        
        inventory.setStockQuantity(inventory.getStockQuantity() - quantity);
        inventoryRepository.save(inventory);
        log.info("Stock updated (decrease): productId={}, quantity=-{}, now={}", productId, quantity, inventory.getStockQuantity());
    }

    @Override
    @Transactional
    public void increaseStock(String productId, Integer quantity) {
        InventoryEntity inventory = inventoryRepository.findByProductIdWithLock(productId)
                .orElseGet(() -> {
                    log.info("Creating initial inventory for new product: {}", productId);
                    return InventoryEntity.builder().productId(productId).stockQuantity(0).build();
                });
        
        inventory.setStockQuantity(inventory.getStockQuantity() + quantity);
        inventoryRepository.save(inventory);
        log.info("Stock updated (increase): productId={}, quantity=+{}, now={}", productId, quantity, inventory.getStockQuantity());
    }

    @Override
    @Transactional
    public void decreaseStockBulk(Map<String, Integer> items) {
        log.info("Bulk stock decrease initiated for {} items", items.size());
        // Sắp xếp theo productId để tránh deadlock khi có nhiều request đồng thời
        List<Map.Entry<String, Integer>> sortedItems = items.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toList());
        sortedItems.forEach(entry -> this.decreaseStock(entry.getKey(), entry.getValue()));
    }

    @Override
    @Transactional
    public void increaseStockBulk(Map<String, Integer> items) {
        log.info("Bulk stock increase initiated for {} items", items.size());
        // Sắp xếp theo productId để tránh deadlock
        List<Map.Entry<String, Integer>> sortedItems = items.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toList());
        sortedItems.forEach(entry -> this.increaseStock(entry.getKey(), entry.getValue()));
    }

    @Override
    @Transactional
    public void createInventory(String productId, Integer initialStock) {
        if (inventoryRepository.findByProductId(productId).isPresent()) {
            log.warn("Attempt to create duplicate inventory for product: {}", productId);
            return; // Idempotent behavior
        }
        
        InventoryEntity inventory = InventoryEntity.builder()
                .productId(productId)
                .stockQuantity(initialStock != null ? initialStock : 0)
                .build();
        inventoryRepository.save(inventory);
        log.info("New inventory record created for product: {}", productId);
    }

    private InventoryEntity findOrThrow(String productId) {
        return inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new InsufficientStockException("Không tìm thấy thông tin tồn kho cho sản phẩm: " + productId));
    }
}
