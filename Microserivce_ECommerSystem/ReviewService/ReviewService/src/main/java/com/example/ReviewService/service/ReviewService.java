package com.example.ReviewService.service;

import com.example.ReviewService.dto.ReviewRequestDTO;
import com.example.ReviewService.dto.ReviewResponseDTO;

import java.util.List;

public interface ReviewService {
    ReviewResponseDTO createReview(ReviewRequestDTO request);
    List<ReviewResponseDTO> getReviewsByProduct(String productId);
    List<ReviewResponseDTO> getReviewsByUser(String userId);
    void deleteReview(String id);
}
