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
public class CouponRedeemRequestDTO {
    private String userId;
    private String orderId;
    private String code;
    private BigDecimal orderAmount; // tiền hàng của đơn — Coupon Service dùng để tính giảm giá và kiểm tra đơn tối thiểu
}
