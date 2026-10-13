package com.example.CouponService.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Sổ ghi từng lần dùng mã: ai, đơn nào, giảm bao nhiêu, đã hoàn chưa.
 * Mỗi đơn hàng chỉ có tối đa 1 dòng (order_id là duy nhất) — nhờ đó gọi lại nhiều lần cho cùng một đơn
 * không trừ lượt 2 lần.
 */
@Entity
@Table(name = "coupon_usage", indexes = @Index(name = "idx_usage_coupon_user", columnList = "couponId,userId"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponUsageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String couponId;

    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false)
    private String userId;

    @Column(name = "order_id", nullable = false, unique = true)
    private String orderId;

    @Column(nullable = false)
    private BigDecimal discountAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CouponUsageStatus status;

    @CreationTimestamp
    private LocalDateTime createdAt;

    private LocalDateTime reversedAt;
}
