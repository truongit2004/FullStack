package com.example.SearchService.kafka;

import com.example.SearchService.service.ProductIndexSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductSyncConsumer {

    private final ProductIndexSyncService syncService;

    @KafkaListener(topics = "product-topic", groupId = "search-group")
    // Không bắt lỗi ở đây: để lỗi nổi lên cho DefaultErrorHandler (ResilienceConfig) thử lại,
    // hết số lần thì chuyển tin sang "<topic>.DLT" thay vì âm thầm bỏ mất.
    // Lưu / xóa theo id nên xử lý lại cùng một tin vẫn an toàn.
    public void consume(String message) throws Exception {
        log.info("Received product event: {}", message);
        syncService.applyEvent(message);
    }
}
