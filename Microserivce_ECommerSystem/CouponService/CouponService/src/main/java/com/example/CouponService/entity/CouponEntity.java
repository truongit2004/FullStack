package com.example.CouponService.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "coupon")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    // Chuỗi khách nhập, luôn lưu dạng CHỮ HOA
    @Column(nullable = false, unique = true, length = 30)
    private String code;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CouponType type;

    // PERCENT: số phần trăm (1-100). FIXED: số tiền giảm.
    @Column(nullable = false)
    private BigDecimal value;

    // Chỉ dùng cho PERCENT: mức giảm tối đa. null = không giới hạn.
    private BigDecimal maxDiscount;

    // Tiền hàng tối thiểu để dùng mã. null = không yêu cầu.
    private BigDecimal minOrderAmount;

    private LocalDateTime startAt;   // null = có hiệu lực ngay
    private LocalDateTime endAt;     // null = không hết hạn

    private Integer usageLimit;      // Tổng lượt dùng. null = không giới hạn.

    @Builder.Default
    private Integer perUserLimit = 1;   // Số lần mỗi khách được dùng

    @Builder.Default
    private Integer usedCount = 0;      // Số lượt đang được tính (đã trừ các lượt hoàn)

    @Builder.Default
    private Boolean active = true;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
