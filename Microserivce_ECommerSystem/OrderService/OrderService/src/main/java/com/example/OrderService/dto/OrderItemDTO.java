package com.example.OrderService.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OrderItemDTO {

    @NotBlank(message = "ProductId không được để trống")
    private String productId;

    @Min(value = 1, message = "Số lượng phải >= 1")
    private Integer quantity;
}