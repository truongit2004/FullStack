package com.example.ProductService.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProductResponseDTO {
    private String id;
    private String name;
    private String description;
    private BigDecimal price;
    private BigDecimal originalPrice;
    private boolean isOnSale;
    private Integer discountPercentage;
    private String categoryId;
    private String categoryName;
    private String imageUrl;
    private Integer stockQuantity;
    private String productStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Helper for OrderService if needed
    public BigDecimal getDiscountedPrice() {
        return price; 
    }
}