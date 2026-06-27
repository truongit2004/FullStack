package com.example.InventoryService.controller;

import com.example.InventoryService.dto.InventoryResponseDTO;
import com.example.InventoryService.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/{productId}")
    public ResponseEntity<InventoryResponseDTO> getStock(@PathVariable String productId) {
        return ResponseEntity.ok(inventoryService.getStock(productId));
    }

    @PostMapping("/decrease")
    public ResponseEntity<Void> decreaseStock(@RequestParam String productId, @RequestParam Integer quantity) {
        inventoryService.decreaseStock(productId, quantity);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/decrease-bulk")
    public ResponseEntity<Void> decreaseStockBulk(@RequestBody java.util.Map<String, Integer> items) {
        inventoryService.decreaseStockBulk(items);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/increase")
    public ResponseEntity<Void> increaseStock(@RequestParam String productId, @RequestParam Integer quantity) {
        inventoryService.increaseStock(productId, quantity);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/increase-bulk")
    public ResponseEntity<Void> increaseStockBulk(@RequestBody java.util.Map<String, Integer> items) {
        inventoryService.increaseStockBulk(items);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/create")
    public ResponseEntity<Void> createInventory(@RequestParam String productId, @RequestParam(required = false) Integer initialStock) {
        inventoryService.createInventory(productId, initialStock);
        return ResponseEntity.ok().build();
    }
}
