package com.example.ReviewService.controller;

import com.example.ReviewService.dto.ReviewRequestDTO;
import com.example.ReviewService.dto.ReviewResponseDTO;
import com.example.ReviewService.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping
    public ResponseEntity<ReviewResponseDTO> createReview(@Valid @RequestBody ReviewRequestDTO request) {
        ReviewResponseDTO response = reviewService.createReview(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/product/{productId}")
    public ResponseEntity<List<ReviewResponseDTO>> getReviewsByProduct(@PathVariable String productId) {
        return ResponseEntity.ok(reviewService.getReviewsByProduct(productId));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<ReviewResponseDTO>> getReviewsByUser(@PathVariable String userId) {
        return ResponseEntity.ok(reviewService.getReviewsByUser(userId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteReview(@PathVariable String id) {
        reviewService.deleteReview(id);
        return ResponseEntity.ok("Review deleted successfully");
    }
}
