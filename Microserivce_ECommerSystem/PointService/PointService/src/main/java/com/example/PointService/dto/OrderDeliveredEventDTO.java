package com.example.PointService.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Payload nhận từ topic "order-delivered-topic" do OrderService phát ra khi
 * đơn hàng chuyển sang DELIVERED. Topic riêng, tách khỏi order-placed-topic,
 * để không ảnh hưởng tới các consumer sẵn có (Notification/Payment).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDeliveredEventDTO {
    private String orderId;
    private String userId;
    private BigDecimal amount;
}
