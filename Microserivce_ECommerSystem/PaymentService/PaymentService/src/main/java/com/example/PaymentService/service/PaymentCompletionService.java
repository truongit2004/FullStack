package com.example.PaymentService.service;

import com.example.PaymentService.client.OrderClient;
import com.example.PaymentService.dto.NotificationEvent;
import com.example.PaymentService.entity.PaymentRecordEntity;
import com.example.PaymentService.repository.PaymentRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Ghi nhận một lần thanh toán thành công và bảo đảm Order Service CUỐI CÙNG cũng được báo.
 *
 * Trước đây: gọi Order Service trước, lỗi thì không lưu gì — khách đã bị trừ tiền nhưng hệ thống
 * không còn dấu vết nào và đơn mãi ở trạng thái chưa thanh toán.
 * Bây giờ: LƯU bản ghi thanh toán trước (orderSynced = false), rồi mới báo Order Service.
 * Báo không được thì bản ghi vẫn còn, và retryUnsyncedPayments() thử lại định kỳ cho tới khi xong.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentCompletionService {

    private static final String STATUS_SUCCESS = "SUCCESS";
    // Vẫn thử lại mãi, nhưng sau ngần này lần thì ghi log mức ERROR để có người vào xem
    private static final int ALERT_AFTER_ATTEMPTS = 20;

    private final PaymentRecordRepository paymentRecordRepository;
    private final OrderClient orderClient;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void recordSuccess(String orderId, String transactionId, String vnpAmount) {
        log.info("Processing success payment for order: {}", orderId);

        // VNPay (hoặc người dùng tải lại trang) có thể gọi callback nhiều lần cho cùng một giao dịch
        PaymentRecordEntity record = paymentRecordRepository
                .findFirstByOrderIdAndTransactionId(orderId, transactionId)
                .orElse(null);

        if (record == null) {
            BigDecimal finalAmount = vnpAmount != null
                    ? new BigDecimal(vnpAmount).divide(new BigDecimal(100))
                    : BigDecimal.ZERO;

            // 1. Lưu trước: từ đây khoản tiền khách đã trả luôn có dấu vết, dù các bước sau có lỗi
            record = paymentRecordRepository.save(PaymentRecordEntity.builder()
                    .orderId(orderId)
                    .transactionId(transactionId)
                    .amount(finalAmount)
                    .paymentMethod("VNPAY_SIMULATED")
                    .responseCode("00")
                    .status(STATUS_SUCCESS)
                    .orderSynced(false)
                    .syncAttempts(0)
                    .build());
        } else if (!Boolean.FALSE.equals(record.getOrderSynced())) {
            log.info("Giao dịch {} của đơn {} đã được xử lý trước đó, bỏ qua.", transactionId, orderId);
            return;
        }

        // 2. Báo Order Service. Lỗi thì KHÔNG ném ra: khách vẫn được đưa về trang thành công,
        //    đơn sẽ chuyển "đã thanh toán" khi lần thử lại kế tiếp thành công.
        syncWithOrderService(record);
    }

    /** Thử lại các khoản thanh toán chưa báo được cho Order Service. */
    @Scheduled(fixedDelayString = "${payment.order-sync.interval-ms:30000}",
            initialDelayString = "${payment.order-sync.initial-delay-ms:20000}")
    public void retryUnsyncedPayments() {
        List<PaymentRecordEntity> unsynced = paymentRecordRepository
                .findTop50ByStatusAndOrderSyncedFalseOrderByCreatedAtAsc(STATUS_SUCCESS);
        for (PaymentRecordEntity record : unsynced) {
            syncWithOrderService(record);
        }
    }

    /** @return true nếu Order Service đã ghi nhận đơn đã thanh toán */
    boolean syncWithOrderService(PaymentRecordEntity record) {
        try {
            // Order Service bỏ qua nếu đơn đã PAID/đang giao, nên gọi lại nhiều lần vẫn an toàn
            orderClient.markOrderAsPaid(record.getOrderId());
        } catch (Exception e) {
            int attempts = (record.getSyncAttempts() == null ? 0 : record.getSyncAttempts()) + 1;
            record.setSyncAttempts(attempts);
            String error = e.getMessage();
            record.setLastSyncError(error != null && error.length() > 500 ? error.substring(0, 500) : error);
            paymentRecordRepository.save(record);

            if (attempts >= ALERT_AFTER_ATTEMPTS) {
                log.error("❌ Đơn {} ĐÃ THANH TOÁN (giao dịch {}) nhưng vẫn chưa báo được cho Order Service sau {} lần: {}",
                        record.getOrderId(), record.getTransactionId(), attempts, error);
            } else {
                log.warn("⏳ Chưa báo được Order Service cho đơn {} (lần {}), sẽ thử lại: {}",
                        record.getOrderId(), attempts, error);
            }
            return false;
        }

        record.setOrderSynced(true);
        record.setLastSyncError(null);
        paymentRecordRepository.save(record);
        log.info("✅ Order Service đã ghi nhận thanh toán của đơn {}", record.getOrderId());

        publishPaymentSuccess(record);
        return true;
    }

    // Email xác nhận thanh toán. Chỉ phát 1 lần, sau khi đơn đã thực sự chuyển trạng thái.
    private void publishPaymentSuccess(PaymentRecordEntity record) {
        try {
            NotificationEvent event = NotificationEvent.builder()
                    .orderId(record.getOrderId())
                    // Giữ định dạng cũ của VNPay (số tiền x 100) vì Notification Service chia 100 khi hiển thị
                    .amount(record.getAmount().multiply(new BigDecimal(100)).toBigInteger().toString())
                    .status(STATUS_SUCCESS)
                    .type("PAYMENT_SUCCESS")
                    .build();
            kafkaTemplate.send("payment-success-topic", objectMapper.writeValueAsString(event));
            log.info("Sent payment success notification to Kafka");
        } catch (Exception e) {
            log.error("Failed to send Kafka notification (Kafka might be disabled): {}", e.getMessage());
        }
    }
}
