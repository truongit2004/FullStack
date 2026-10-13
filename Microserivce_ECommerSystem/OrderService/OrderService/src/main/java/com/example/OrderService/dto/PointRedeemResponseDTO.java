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
public class PointRedeemResponseDTO {
    private String orderId;
    private BigDecimal pointsRedeemed;
    private BigDecimal discountAmount;
    private BigDecimal balanceAfter;
}
