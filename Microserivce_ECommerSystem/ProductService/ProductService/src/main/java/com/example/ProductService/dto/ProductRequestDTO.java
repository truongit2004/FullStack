package com.example.ProductService.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.*;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
@Data
@Setter
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor

public class ProductRequestDTO {
//    @JsonProperty
//    @NotBlank(message = "Không được để trống")
    private String name;

    private String description;

//    @Positive(message = "Price phải > 0")
    private BigDecimal price;

    @NotBlank(message = "CategoryId không được để trống")
    private String categoryId;

//    @Min(value = 0, message = "Stock >= 0")
    private Integer stockQuantity;

    private MultipartFile imageUrl;

    private Boolean deleteImage;
}
