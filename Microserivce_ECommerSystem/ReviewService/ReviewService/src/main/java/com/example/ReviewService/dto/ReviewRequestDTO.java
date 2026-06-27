package com.example.ReviewService.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReviewRequestDTO {
    
    @NotBlank(message = "Product ID is required")
    private String productId;
    
    @NotBlank(message = "User ID is required")
    private String userId;
    
    @NotNull(message = "Rating is required")
    @Min(value = 1, message = "Rating must be at least 1")
    @Max(value = 5, message = "Rating must be at most 5")
    private Integer rating;
    
    @NotBlank(message = "Comment is required")
    private String comment;
}
