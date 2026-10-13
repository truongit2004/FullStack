package com.example.CouponService.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

    @NotBlank(message = "userId không được để trống")
    private String userId;

    @NotBlank(message = "orderId không được để trống")
    private String orderId;

    @NotBlank(message = "code không được để trống")
    private String code;

    // Tiền hàng của đơn (chưa gồm phí vận chuyển) — cơ sở để tính giảm giá và kiểm tra đơn tối thiểu
    @NotNull(message = "orderAmount không được để trống")
    private BigDecimal orderAmount;
}
