package com.example.OrderService.dto;

import lombok.*;
import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProductResponseDTO {
    private String id;
    private String name;
    private String imageUrl;
    private BigDecimal price;
    private BigDecimal discountedPrice; // giá sau giảm (ngày lễ)
    private boolean onSale;
    private Integer stockQuantity;
    private String productStatus;
}