package com.example.ProductService.dto;

import lombok.*;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CategoryDTO {
    private String id;
    private String name;
    private String description;
    private List<ProductResponseDTO> products;
}
