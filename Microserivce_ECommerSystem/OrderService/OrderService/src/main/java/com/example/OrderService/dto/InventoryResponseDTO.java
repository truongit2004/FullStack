package com.example.OrderService.dto;

import lombok.Data;

@Data
public class InventoryResponseDTO {
    private String productId;
    private Integer stockQuantity;
}
