package com.example.ProductService.service;

import com.example.ProductService.dto.ProductResponseDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;

/**
 * Báo cho Search Service mỗi khi sản phẩm thay đổi (tạo, sửa, xóa, đổi giá), qua topic "product-topic",
 * để kết quả tìm kiếm khớp với dữ liệu thật. Trước đây chỉ báo khi TẠO sản phẩm.
 *
 * Sự kiện được phát SAU KHI transaction commit, để Search Service không lập chỉ mục dữ liệu
 * rồi bị rollback. Phát lỗi (Kafka chết) chỉ ghi log: Search Service tự đối soát lại định kỳ.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductSearchPublisher {

    private static final String PRODUCT_TOPIC = "product-topic";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /** Sản phẩm được tạo hoặc thay đổi nội dung / giá / trạng thái. */
    public void publishUpsert(ProductResponseDTO product) {
        afterCommit(() -> send(product.getId(), product));
    }

    /** Sản phẩm bị xóa (mềm hoặc cứng): gỡ khỏi kết quả tìm kiếm. */
    public void publishDeleted(String productId) {
        afterCommit(() -> send(productId, Map.of("id", productId, "deleted", true)));
    }

    private void send(String productId, Object payload) {
        try {
            kafkaTemplate.send(PRODUCT_TOPIC, productId, objectMapper.writeValueAsString(payload));
            log.info("Sent product event to Kafka for SearchService: {}", productId);
        } catch (Exception e) {
            log.error("Failed to send product event to Kafka for id {}: {}", productId, e.getMessage());
        }
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
