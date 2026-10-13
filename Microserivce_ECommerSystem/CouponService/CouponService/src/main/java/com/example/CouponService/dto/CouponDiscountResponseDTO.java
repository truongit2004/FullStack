package com.example.CouponService.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Kết quả xem trước (preview) hoặc dùng mã (redeem): mã nào, giảm bao nhiêu. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponDiscountResponseDTO {
    private String orderId;          // null khi chỉ xem trước
    private String code;
    private String description;
    private BigDecimal discountAmount;
}
