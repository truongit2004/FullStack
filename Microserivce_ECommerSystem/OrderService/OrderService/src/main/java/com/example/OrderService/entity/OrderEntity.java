package com.example.OrderService.entity;


import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "orders")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String userId;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<OrderItemEntity> items;

    private BigDecimal totalAmount;    // tổng tiền sản phẩm
    private BigDecimal shippingFee;    // phí ship
    private BigDecimal discountAmount; // giảm giá
    private BigDecimal finalAmount;    // totalAmount - discountAmount + shippingFee
    @Column(name = "total_price")
    private BigDecimal totalPrice;
    private String shippingAddress;
    private String note;

    @Builder.Default
    private Boolean deleted = false;

    private LocalDateTime deletedAt;
    private LocalDateTime cancelledAt;
    private LocalDateTime deliveredAt;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
