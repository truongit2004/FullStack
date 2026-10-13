package com.example.PaymentService;

import com.example.PaymentService.client.OrderClient;
import com.example.PaymentService.entity.PaymentRecordEntity;
import com.example.PaymentService.repository.PaymentRecordRepository;
import com.example.PaymentService.service.PaymentCompletionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Kiểm tra: khách thanh toán thành công đúng lúc Order Service chết thì khoản thanh toán vẫn được lưu
 * và đơn được đánh dấu đã thanh toán khi Order Service hoạt động trở lại.
 *
 * Cần MySQL đang chạy ở localhost; dùng database riêng "payment_service_resilience_test".
 * Order Service và Kafka được giả lập.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:mysql://localhost:3306/payment_service_resilience_test?createDatabaseIfNotExist=true",
        "spring.jpa.show-sql=false",
        "eureka.client.enabled=false",
        "spring.kafka.listener.auto-startup=false",
        // Tắt lịch chạy nền: test tự gọi retryUnsyncedPayments()
        "payment.order-sync.initial-delay-ms=3600000",
        "payment.order-sync.interval-ms=3600000"
})
class PaymentResilienceTest {

    @Autowired PaymentCompletionService completionService;
    @Autowired PaymentRecordRepository repository;

    @MockitoBean OrderClient orderClient;
    @MockitoBean KafkaTemplate<String, String> kafkaTemplate;

    @BeforeEach
    void clean() {
        repository.deleteAll();
        when(kafkaTemplate.send(anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
    }

    @Test
    void paymentIsRecorded_evenWhenOrderServiceIsDown_andSyncedLater() {
        doThrow(new RuntimeException("order-service down")).when(orderClient).markOrderAsPaid("order-1");

        // Không được ném lỗi: khách đã trả tiền, callback phải hoàn tất
        assertDoesNotThrow(() -> completionService.recordSuccess("order-1", "TXN-1", "10800000"));

        List<PaymentRecordEntity> records = repository.findAll();
        assertEquals(1, records.size(), "Khoản thanh toán phải được lưu dù Order Service chết");
        PaymentRecordEntity record = records.get(0);
        assertEquals("SUCCESS", record.getStatus());
        assertEquals(0, new BigDecimal("108000").compareTo(record.getAmount()));
        assertEquals(Boolean.FALSE, record.getOrderSynced());
        assertEquals(1, record.getSyncAttempts());
        assertNotNull(record.getLastSyncError());
        verify(kafkaTemplate, never()).send(anyString(), anyString());

        // Order Service vẫn chết
        completionService.retryUnsyncedPayments();
        assertEquals(2, repository.findAll().get(0).getSyncAttempts());

        // Order Service sống lại
        doNothing().when(orderClient).markOrderAsPaid("order-1");
        completionService.retryUnsyncedPayments();

        record = repository.findAll().get(0);
        assertEquals(Boolean.TRUE, record.getOrderSynced());
        assertNull(record.getLastSyncError());
        verify(orderClient, times(3)).markOrderAsPaid("order-1");
        verify(kafkaTemplate, times(1)).send(eq("payment-success-topic"), contains("\"amount\":\"10800000\""));

        // Đã xong thì không gọi nữa
        completionService.retryUnsyncedPayments();
        verify(orderClient, times(3)).markOrderAsPaid("order-1");
        verify(kafkaTemplate, times(1)).send(anyString(), anyString());
    }

    @Test
    void happyPath_syncsImmediately() {
        completionService.recordSuccess("order-2", "TXN-2", "5000000");

        PaymentRecordEntity record = repository.findAll().get(0);
        assertEquals(Boolean.TRUE, record.getOrderSynced());
        verify(orderClient).markOrderAsPaid("order-2");
        verify(kafkaTemplate).send(eq("payment-success-topic"), contains("order-2"));
    }

    @Test
    void duplicateCallback_doesNotCreateSecondRecord_orNotifyTwice() {
        completionService.recordSuccess("order-3", "TXN-3", "5000000");
        completionService.recordSuccess("order-3", "TXN-3", "5000000");

        assertEquals(1, repository.count());
        verify(orderClient, times(1)).markOrderAsPaid("order-3");
        verify(kafkaTemplate, times(1)).send(anyString(), anyString());
    }

    @Test
    void duplicateCallback_whileStillUnsynced_retriesWithoutDuplicating() {
        doThrow(new RuntimeException("down")).when(orderClient).markOrderAsPaid("order-4");
        completionService.recordSuccess("order-4", "TXN-4", "5000000");

        doNothing().when(orderClient).markOrderAsPaid("order-4");
        completionService.recordSuccess("order-4", "TXN-4", "5000000");

        assertEquals(1, repository.count());
        assertEquals(Boolean.TRUE, repository.findAll().get(0).getOrderSynced());
    }

    @Test
    void legacyRecordsWithoutSyncFlag_areNotRetried() {
        // Bản ghi tạo trước khi có cột orderSynced (null) coi như đã xong
        repository.save(PaymentRecordEntity.builder()
                .orderId("old-order").transactionId("OLD").amount(BigDecimal.TEN).status("SUCCESS").build());

        completionService.retryUnsyncedPayments();

        verifyNoInteractions(orderClient);
    }
}
