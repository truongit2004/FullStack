package com.example.CouponService.dto;

import com.example.CouponService.entity.CouponType;
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
public class CouponResponseDTO {
    private String id;
    private String code;
    private String description;
    private CouponType type;
    private BigDecimal value;
    private BigDecimal maxDiscount;
    private BigDecimal minOrderAmount;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Integer usageLimit;
    private Integer perUserLimit;
    private Integer usedCount;
    private Boolean active;
    private LocalDateTime createdAt;
}
