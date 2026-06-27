package com.example.User.Service.dto.external;

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
    private String categoryId;
    private String imageUrl;
    private Integer stockQuantity;
    private String productStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
