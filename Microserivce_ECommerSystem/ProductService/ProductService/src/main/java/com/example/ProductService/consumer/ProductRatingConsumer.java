package com.example.ProductService.consumer;

import com.example.ProductService.entity.ProductEntity;
import com.example.ProductService.repository.ProductRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductRatingConsumer {

    private final ProductRepository productRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "product-rating-topic", groupId = "product-service-group")
    @Transactional
    public void listenProductRating(String message) {
        log.info("📥 Received rating update message: {}", message);
        try {
            JsonNode jsonNode = objectMapper.readTree(message);
            String productId = jsonNode.get("productId").asText();
            int newRating = jsonNode.get("rating").asInt();

            productRepository.findById(productId).ifPresent(product -> {
                updateProductRating(product, newRating);
                productRepository.save(product);
                log.info("⭐ Updated product {} average rating to {} (Total reviews: {})", 
                        product.getName(), product.getAverageRating(), product.getReviewCount());
            });
        } catch (Exception e) {
            log.error("❌ Error processing Kafka message: {}", e.getMessage());
        }
    }

    private void updateProductRating(ProductEntity product, int newRating) {
        int oldCount = product.getReviewCount() != null ? product.getReviewCount() : 0;
        double oldAverage = product.getAverageRating() != null ? product.getAverageRating() : 0.0;

        int newCount = oldCount + 1;
        double newAverage = ((oldAverage * oldCount) + newRating) / newCount;

        // Làm tròn đến 1 chữ số thập phân
        newAverage = Math.round(newAverage * 10.0) / 10.0;

        product.setReviewCount(newCount);
        product.setAverageRating(newAverage);
    }
}
