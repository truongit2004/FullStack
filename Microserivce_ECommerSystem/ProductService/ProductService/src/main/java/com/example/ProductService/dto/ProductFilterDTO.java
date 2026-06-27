package com.example.ProductService.dto;


import lombok.*;
import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProductFilterDTO {
    private String name;
    private String categoryId;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private Integer minStock;
    private Integer maxStock;
    private String productStatus;
    private Boolean onSale;
    private String sortBy;  // price | name | createdAt
    private String sortDir;
}