package com.example.ReviewService.service.impl;

import com.example.ReviewService.client.OrderClient;
import com.example.ReviewService.client.ProductClient;
import com.example.ReviewService.dto.ReviewRequestDTO;
import com.example.ReviewService.dto.ReviewResponseDTO;
import com.example.ReviewService.entity.ReviewEntity;
import com.example.ReviewService.mapper.ReviewMapper;
import com.example.ReviewService.repository.ReviewRepository;
import com.example.ReviewService.service.ReviewService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final OrderClient orderClient;
    private final ProductClient productClient;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public ReviewResponseDTO createReview(ReviewRequestDTO request) {
        // 1. Kiểm tra đăng nhập và UserId có khớp không
        String currentUserId = SecurityContextHolder.getContext().getAuthentication().getName();
        if (!currentUserId.equals(request.getUserId())) {
            throw new RuntimeException("Unauthorized: You can only create reviews for yourself.");
        }

        // 2. Kiểm tra sản phẩm có tồn tại không
        try {
            productClient.getProductById(request.getProductId());
        } catch (Exception e) {
            throw new RuntimeException("Product not found or ProductService is unavailable.");
        }

        // 3. Kiểm tra xem người dùng đã mua sản phẩm và đơn hàng đã được giao (Status: DELIVERED) chưa
        try {
            Boolean hasPurchased = orderClient.checkPurchased(request.getUserId(), request.getProductId());
            if (hasPurchased == null || !hasPurchased) {
                throw new RuntimeException("You must have a delivered order for this product before reviewing.");
            }
        } catch (Exception e) {
            if (e instanceof RuntimeException && e.getMessage().contains("You must")) {
                throw e;
            }
            log.error("Error calling OrderService: {}", e.getMessage());
            throw new RuntimeException("Could not verify order status. Please try again later.");
        }

        // 4. Check if already reviewed (Existing check)
        if (reviewRepository.existsByUserIdAndProductId(request.getUserId(), request.getProductId())) {
            throw new RuntimeException("User has already reviewed this product.");
        }

        ReviewEntity entity = ReviewMapper.toEntity(request);
        ReviewEntity saved = reviewRepository.save(entity);

        // 3. Notify ProductService via Kafka to update average rating (Async to prevent
        // blocking)
        sendRatingUpdate(saved.getProductId(), saved.getRating());

        return ReviewMapper.toResponse(saved);
    }

    private void sendRatingUpdate(String productId, int rating) {
        CompletableFuture.runAsync(() -> {
            try {
                Map<String, Object> event = new HashMap<>();
                event.put("productId", productId);
                event.put("rating", rating);
                event.put("type", "NEW_REVIEW");

                String jsonEvent = objectMapper.writeValueAsString(event);
                kafkaTemplate.send("product-rating-topic", jsonEvent);
                log.info("✅ Sent rating update to Kafka for productId: {}", productId);
            } catch (Exception e) {
                log.error("❌ Failed to send Kafka message: {}", e.getMessage());
            }
        });
    }

    @Override
    public List<ReviewResponseDTO> getReviewsByProduct(String productId) {
        return reviewRepository.findByProductIdOrderByCreatedAtDesc(productId)
                .stream()
                .map(ReviewMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<ReviewResponseDTO> getReviewsByUser(String userId) {
        return reviewRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(ReviewMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteReview(String id) {
        ReviewEntity entity = reviewRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Review not found with id: " + id));

        // Kiểm tra quyền xóa (chỉ người tạo review mới được xóa)
        String currentUserId = SecurityContextHolder.getContext().getAuthentication().getName();
        if (!currentUserId.equals(entity.getUserId())) {
            throw new RuntimeException("Unauthorized: You can only delete your own reviews.");
        }

        reviewRepository.delete(entity);
    }
}
