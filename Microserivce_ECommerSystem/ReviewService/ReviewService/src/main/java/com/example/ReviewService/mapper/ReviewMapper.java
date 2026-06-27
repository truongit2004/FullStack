package com.example.ReviewService.mapper;

import com.example.ReviewService.dto.ReviewRequestDTO;
import com.example.ReviewService.dto.ReviewResponseDTO;
import com.example.ReviewService.entity.ReviewEntity;

public class ReviewMapper {

    public static ReviewEntity toEntity(ReviewRequestDTO request) {
        if (request == null) {
            return null;
        }
        return ReviewEntity.builder()
                .productId(request.getProductId())
                .userId(request.getUserId())
                .rating(request.getRating())
                .comment(request.getComment())
                .build();
    }

    public static ReviewResponseDTO toResponse(ReviewEntity entity) {
        if (entity == null) {
            return null;
        }
        return ReviewResponseDTO.builder()
                .id(entity.getId())
                .productId(entity.getProductId())
                .userId(entity.getUserId())
                .rating(entity.getRating())
                .comment(entity.getComment())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
