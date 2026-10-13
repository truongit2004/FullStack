package com.example.OrderService.retry;

import com.example.OrderService.client.CouponClient;
import com.example.OrderService.client.PointClient;
import com.example.OrderService.client.ShippingClient;
import com.example.OrderService.client.UserClient;
import com.example.OrderService.dto.PointEventDTO;
import com.example.OrderService.dto.ShippingRequestDTO;
import com.example.OrderService.dto.UserResponseDTO;
import com.example.OrderService.entity.OrderEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Các việc phụ của đơn hàng phải gọi ra ngoài (Point Service, Shipping Service, Kafka).
 * Mỗi phương thức NÉM LỖI khi thất bại — nơi gọi quyết định ghi lại để thử lại sau
 * (xem PendingTaskService / PendingTaskProcessor). Tất cả đều an toàn khi chạy lại nhiều lần.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderSideEffects {

    public static final String POINT_TOPIC = "order-delivered-topic";

    private static final String DEFAULT_CARRIER = "GHTK";
    private static final String DEFAULT_RECIPIENT = "Khách hàng";
    private static final String DEFAULT_PHONE = "N/A";

    private final PointClient pointClient;
    private final CouponClient couponClient;
    private final ShippingClient shippingClient;
    private final UserClient userClient;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /** Hoàn điểm đã dùng / thu hồi điểm đã tích của đơn. Point Service tự chống xử lý trùng theo orderId. */
    public void reversePoints(String orderId) {
        pointClient.reverse(orderId);
    }

    /** Hoàn lượt dùng mã giảm giá của đơn. Coupon Service bỏ qua nếu đơn không dùng mã hoặc đã hoàn. */
    public void reverseCoupon(String orderId) {
        couponClient.reverse(orderId);
    }

    /** Tạo vận đơn cho đơn hàng nếu chưa có. */
    public void createShippingIfMissing(OrderEntity order) {
        try {
            if (shippingClient.getByOrderId(order.getId()) != null) {
                log.info("ℹ️ Shipping ticket already exists for order: {}, skipping creation.", order.getId());
                return;
            }
        } catch (FeignException.NotFound e) {
            // 404 = chưa có vận đơn -> tạo mới bên dưới. Lỗi khác (service chết) được ném ra để thử lại sau.
        }

        String recipientName = DEFAULT_RECIPIENT;
        String recipientPhone = DEFAULT_PHONE;
        try {
            UserResponseDTO user = userClient.getUserById(order.getUserId());
            if (user != null) {
                if (user.getUsername() != null && !user.getUsername().isEmpty()) {
                    recipientName = user.getUsername();
                } else if (user.getEmail() != null && !user.getEmail().isEmpty()) {
                    recipientName = user.getEmail();
                }
                if (user.getPhone() != null && !user.getPhone().isEmpty()) {
                    recipientPhone = user.getPhone();
                }
            }
        } catch (Exception e) {
            // Thiếu tên người nhận không đáng để chặn việc tạo vận đơn
            log.warn("Could not fetch user details for shipping: {}", e.getMessage());
        }

        shippingClient.createShipping(ShippingRequestDTO.builder()
                .orderId(order.getId())
                .carrier(DEFAULT_CARRIER)
                .recipientName(recipientName)
                .recipientAddress(order.getShippingAddress() != null ? order.getShippingAddress() : "N/A")
                .recipientPhone(recipientPhone)
                .build());
        log.info("✅ Successfully created shipping ticket for order: {}", order.getId());
    }

    /**
     * Phát sự kiện cộng điểm và CHỜ Kafka xác nhận đã nhận (tối đa 10 giây).
     * Point Service chống cộng trùng theo orderId nên phát lại nhiều lần vẫn an toàn.
     */
    public void publishPointEarn(OrderEntity order) throws Exception {
        PointEventDTO event = PointEventDTO.builder()
                .orderId(order.getId())
                .userId(order.getUserId())
                // Tích điểm trên giá trị gốc (tiền hàng), không tính phí ship / giảm giá bằng điểm
                .amount(order.getTotalAmount())
                .build();

        kafkaTemplate.send(POINT_TOPIC, objectMapper.writeValueAsString(event)).get(10, TimeUnit.SECONDS);
        log.info("✅ Published ORDER_DELIVERED point-earn event for orderId {}", order.getId());
    }
}
