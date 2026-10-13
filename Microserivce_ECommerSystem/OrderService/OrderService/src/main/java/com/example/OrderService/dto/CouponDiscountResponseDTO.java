package com.example.OrderService.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponDiscountResponseDTO {
    private String orderId;
    private String code;
    private String description;
    private BigDecimal discountAmount;
}
