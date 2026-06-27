package com.example.PaymentService.consumer;

import com.example.PaymentService.entity.PaymentRecordEntity;
import com.example.PaymentService.repository.PaymentRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentConsumer {

    private final PaymentRecordRepository paymentRecordRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "order-placed-topic", groupId = "payment-group")
    public void consumeOrderEvents(String message) {
        log.info("🔔 PaymentService nhận tin nhắn từ Kafka: {}", message);
        try {
            Map<String, Object> event = objectMapper.readValue(message, Map.class);
            String type = (String) event.get("type");
            String orderId = (String) event.get("orderId");

            if ("ORDER_RETURNED".equals(type)) {
                log.info("🔄 Đang xử lý hoàn trả tiền cho đơn hàng: {}", orderId);
                Optional<PaymentRecordEntity> recordOpt = paymentRecordRepository.findByOrderId(orderId);
                
                if (recordOpt.isPresent()) {
                    PaymentRecordEntity record = recordOpt.get();
                    record.setStatus("REFUNDED");
                    paymentRecordRepository.save(record);
                    log.info("✅ Đã cập nhật trạng thái thanh toán đơn hàng {} thành REFUNDED trong CSDL.", orderId);
                } else {
                    log.warn("⚠️ Không tìm thấy bản ghi thanh toán cho đơn hàng {} để hoàn tiền.", orderId);
                }
            }
        } catch (Exception e) {
            log.error("❌ Lỗi xử lý sự kiện Kafka trong PaymentService: {}", e.getMessage());
        }
    }
}
