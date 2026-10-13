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
    // Không bắt lỗi ở đây: để lỗi nổi lên cho DefaultErrorHandler (ResilienceConfig) thử lại,
    // hết số lần thì chuyển tin sang "<topic>.DLT" thay vì âm thầm bỏ mất.
    // Lỗi thì transaction rollback, nên thử lại không cộng điểm đánh giá 2 lần.
    public void listenProductRating(String message) throws Exception {
        log.info("📥 Received rating update message: {}", message);
        {
            JsonNode jsonNode = objectMapper.readTree(message);
            String productId = jsonNode.get("productId").asText();
            int newRating = jsonNode.get("rating").asInt();

            productRepository.findById(productId).ifPresent(product -> {
                updateProductRating(product, newRating);
                productRepository.save(product);
                log.info("⭐ Updated product {} average rating to {} (Total reviews: {})", 
                        product.getName(), product.getAverageRating(), product.getReviewCount());
            });
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
