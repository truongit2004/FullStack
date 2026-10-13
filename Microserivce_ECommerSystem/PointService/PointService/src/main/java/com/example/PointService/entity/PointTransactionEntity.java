package com.example.PointService.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Sổ cái (ledger) điểm thưởng — mọi thay đổi số dư đều phải đi qua 1 dòng ở đây,
 * để có thể audit và đảo ngược (reverse) chính xác khi đơn hàng bị hủy/trả.
 */
@Entity
@Table(name = "point_transaction")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PointTransactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String userId;

    @Column(name = "order_id")
    private String orderId;

    @Enumerated(EnumType.STRING)
    private PointTransactionType type;

    private BigDecimal points; // luôn dương, chiều cộng/trừ xác định bởi "type"

    private BigDecimal balanceAfter;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
