package com.example.User.Service.dto.external;

import lombok.*;
import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OrderItemResponseDTO {
    private String id;
    private String productId;
    private String productName;
    private String productImage;
    private BigDecimal price;
    private Integer quantity;
    private BigDecimal subtotal;
    
    // We will inject detailed Product Data here after fetching from Product Service
    private ProductResponseDTO productDetails;
}
