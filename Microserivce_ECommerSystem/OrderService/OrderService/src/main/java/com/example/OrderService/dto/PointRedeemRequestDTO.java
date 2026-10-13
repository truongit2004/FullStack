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
public class PointRedeemRequestDTO {
    private String userId;
    private String orderId;
    private BigDecimal points;
    private BigDecimal orderAmount; // tiền hàng của đơn — Point Service dùng để kiểm tra giới hạn dùng điểm
}
