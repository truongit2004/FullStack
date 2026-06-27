package com.example.CartService.dto;

import lombok.*;
import java.math.BigDecimal;
import java.util.List;

/**
 * Mirror OrderRequestDTO của Order Service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderRequestDTO {
    private String userId;
    private List<OrderItemDTO> items;
    private String shippingAddress;
    private String note;
    private BigDecimal shippingFee;
    private BigDecimal discountAmount;
}
