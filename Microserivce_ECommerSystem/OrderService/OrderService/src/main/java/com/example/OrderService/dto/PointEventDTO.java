package com.example.OrderService.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Payload publish lên "order-delivered-topic" khi đơn hàng chuyển sang DELIVERED,
 * để PointService cộng điểm tích lũy cho khách. "amount" là giá trị gốc (tiền hàng) của đơn.
 * Dùng topic riêng, không tái dùng order-placed-topic, để không ảnh hưởng tới
 * các consumer hiện có (NotificationService/PaymentService đang parse JSON theo schema cũ).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PointEventDTO {
    private String orderId;
    private String userId;
    private BigDecimal amount;
}
