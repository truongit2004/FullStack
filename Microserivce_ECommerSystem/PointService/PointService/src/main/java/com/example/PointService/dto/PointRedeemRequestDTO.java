package com.example.PointService.dto;

import jakarta.validation.constraints.DecimalMin;
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
public class PointRedeemRequestDTO {

    @NotBlank(message = "userId không được để trống")
    private String userId;

    @NotBlank(message = "orderId không được để trống")
    private String orderId;

    @NotNull(message = "points không được để trống")
    @DecimalMin(value = "0.01", message = "Số điểm sử dụng phải lớn hơn 0")
    private BigDecimal points;

    // Giá trị gốc (tiền hàng) của đơn — dùng để kiểm tra giới hạn point.max-redeem-ratio
    @NotNull(message = "orderAmount không được để trống")
    private BigDecimal orderAmount;
}
