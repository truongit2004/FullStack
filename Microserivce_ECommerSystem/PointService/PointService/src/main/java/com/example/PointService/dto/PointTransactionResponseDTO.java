package com.example.PointService.dto;

import com.example.PointService.entity.PointTransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PointTransactionResponseDTO {
    private String id;
    private String orderId;
    private PointTransactionType type;
    private BigDecimal points;
    private BigDecimal balanceAfter;
    private LocalDateTime createdAt;
}
