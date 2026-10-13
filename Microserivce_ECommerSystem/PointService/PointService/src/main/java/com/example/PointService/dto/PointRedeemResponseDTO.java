package com.example.PointService.dto;

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
    private BigDecimal discountAmount; // quy đổi 1 điểm = 1 VNĐ
    private BigDecimal balanceAfter;
}
