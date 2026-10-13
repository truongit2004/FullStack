package com.example.PointService.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Quy tắc điểm thưởng đang áp dụng (đọc từ point.* trong application.yml).
 * Đây là nguồn duy nhất — FE và các service khác lấy từ đây, không tự hard-code.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PointConfigResponseDTO {
    private BigDecimal earnRate;        // tỉ lệ tích điểm trên giá trị gốc (tiền hàng) của đơn
    private BigDecimal maxRedeemRatio;  // tỉ lệ tối đa của tiền hàng được thanh toán bằng điểm
}
