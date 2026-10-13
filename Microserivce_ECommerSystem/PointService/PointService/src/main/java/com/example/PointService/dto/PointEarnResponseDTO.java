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
public class PointEarnResponseDTO {
    private String orderId;
    private BigDecimal pointsEarned;
    private BigDecimal balanceAfter;
}
