package com.example.CartService.dto;

import lombok.Data;

@Data
public class InventoryResponseDTO {
    private String productId;
    private Integer stockQuantity;
}
