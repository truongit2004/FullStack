package com.example.OrderService.entity;


import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "order_item")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OrderItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private OrderEntity order;

    private String productId;
    private String productName;   // lưu lại tại thời điểm đặt
    private String productImage;  // lưu lại tại thời điểm đặt
    private BigDecimal price;     // lưu lại tại thời điểm đặt
    private Integer quantity;
    private BigDecimal subtotal;  // price × quantity
}