package com.example.ReviewService.repository;

import com.example.ReviewService.entity.ReviewEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReviewRepository extends JpaRepository<ReviewEntity, String> {
    
    // Find all reviews for a specific product
    List<ReviewEntity> findByProductIdOrderByCreatedAtDesc(String productId);
    
    // Find all reviews by a specific user
    List<ReviewEntity> findByUserIdOrderByCreatedAtDesc(String userId);
    
    // Check if a user has already reviewed a product
    boolean existsByUserIdAndProductId(String userId, String productId);
}
