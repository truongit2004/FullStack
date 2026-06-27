package com.example.SearchService.kafka;

import com.example.SearchService.document.ProductDocument;
import com.example.SearchService.repository.ProductSearchRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductSyncConsumer {

    private final ProductSearchRepository repository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "product-topic", groupId = "search-group")
    public void consume(String message) {
        try {
            log.info("Received product event: {}", message);
            ProductDocument product = objectMapper.readValue(message, ProductDocument.class);
            repository.save(product);
            log.info("Synced product to Elasticsearch: {}", product.getId());
        } catch (Exception e) {
            log.error("Failed to sync product: {}", e.getMessage());
        }
    }
}
