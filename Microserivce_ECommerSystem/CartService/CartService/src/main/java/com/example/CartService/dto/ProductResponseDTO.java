package com.example.CartService.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Mirror của ProductResponseDTO bên Product Service.
 * Chỉ lấy những field Cart cần dùng.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponseDTO {
    private String id;
    private String name;
    private String imageUrl;
    private String description;
    private BigDecimal price;
    private Integer stockQuantity;
    private String categoryId;
    private String productStatus;   // "ACTIVE" | "INACTIVE" | ...
    private Boolean deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
