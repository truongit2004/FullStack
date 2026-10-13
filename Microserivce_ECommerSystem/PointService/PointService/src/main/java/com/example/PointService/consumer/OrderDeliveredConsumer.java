package com.example.PointService.consumer;

import com.example.PointService.dto.OrderDeliveredEventDTO;
import com.example.PointService.dto.PointEarnRequestDTO;
import com.example.PointService.service.PointService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Lắng nghe "order-delivered-topic" do OrderService phát ra khi đơn hàng
 * chuyển sang DELIVERED, để tự động cộng điểm (10% giá trị đơn) cho khách.
 * Dùng topic riêng (không dùng chung order-placed-topic) để không ảnh hưởng
 * tới các consumer sẵn có (NotificationService/PaymentService).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderDeliveredConsumer {

    private final PointService pointService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "order-delivered-topic", groupId = "point-service-group")
    // Không bắt lỗi ở đây: để lỗi nổi lên cho DefaultErrorHandler (ResilienceConfig) thử lại,
    // hết số lần thì chuyển tin sang "<topic>.DLT" thay vì âm thầm bỏ mất.
    // earn() chống cộng trùng theo orderId nên xử lý lại cùng một tin vẫn an toàn.
    public void consume(String message) throws Exception {
        log.info("🔔 Nhận ORDER_DELIVERED event: {}", message);
        OrderDeliveredEventDTO event = objectMapper.readValue(message, OrderDeliveredEventDTO.class);
        pointService.earn(PointEarnRequestDTO.builder()
                .userId(event.getUserId())
                .orderId(event.getOrderId())
                .orderAmount(event.getAmount())
                .build());
    }
}
