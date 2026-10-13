package com.example.NotificationService.consumer;

import com.example.NotificationService.dto.NotificationEvent;
import com.example.NotificationService.service.EmailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationConsumer {
    private final EmailService emailService;
    private final ObjectMapper objectMapper;
    private final com.example.NotificationService.client.OrderClient orderClient;
    private final com.example.NotificationService.client.UserClient userClient;

    @KafkaListener(topics = {"payment-success-topic", "order-placed-topic"}, groupId = "notification-group")
    // Không bắt lỗi ở đây: để lỗi nổi lên cho DefaultErrorHandler (ResilienceConfig) thử lại,
    // hết số lần thì chuyển tin sang "<topic>.DLT" thay vì âm thầm bỏ mất.
    // Email là bước cuối cùng nên thử lại không gửi trùng: lỗi xảy ra trước khi mail được gửi đi.
    public void consume(String message) throws Exception {
        log.info("🔔 Nhận tin nhắn từ Kafka: {}", message);
        {
            NotificationEvent event = objectMapper.readValue(message, NotificationEvent.class);
            
            // 1. Lấy thông tin chi tiết đơn hàng (để lấy userId)
            Object orderObj = orderClient.getOrderById(event.getOrderId());
            if (orderObj instanceof java.util.Map) {
                java.util.Map<?, ?> orderMap = (java.util.Map<?, ?>) orderObj;
                String userId = (String) orderMap.get("userId");
                
                // Cập nhật số tiền chuẩn từ đơn hàng nếu chưa có hoặc là ORDER_PLACED
                if (event.getAmount() == null || "ORDER_PLACED".equals(event.getType())) {
                    event.setAmount(orderMap.get("finalAmount").toString());
                }

                // 2. Lấy thông tin Email và Tên từ User Service
                Object userObj = userClient.getUserById(userId);
                if (userObj instanceof java.util.Map) {
                    java.util.Map<?, ?> userMap = (java.util.Map<?, ?>) userObj;
                    event.setEmail((String) userMap.get("email"));
                    event.setCustomerName((String) userMap.get("username"));
                }
            }

            // 3. Định dạng lại số tiền
            if (event.getAmount() != null) {
                try {
                    double rawAmount = Double.parseDouble(event.getAmount());
                    if ("PAYMENT_SUCCESS".equals(event.getType())) {
                        event.setAmount(String.format("%,.0f", rawAmount / 100));
                    } else {
                        event.setAmount(String.format("%,.0f", rawAmount));
                    }
                } catch (Exception e) {
                    log.warn("Không thể định dạng số tiền: {}", event.getAmount());
                }
            }

            if (event.getEmail() != null) {
                if ("ORDER_PLACED".equals(event.getType())) {
                    emailService.sendOrderPlacedEmail(event);
                } else if ("ORDER_RETURNED".equals(event.getType())) {
                    emailService.sendOrderReturnedEmail(event);
                } else {
                    emailService.sendPaymentSuccessEmail(event);
                }
            } else {
                log.warn("⚠️ Không thể tìm thấy Email cho đơn hàng {}, hủy lệnh gửi mail.", event.getOrderId());
            }

        }
    }
}
