package com.example.PointService.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PointEarnRequestDTO {

    @NotBlank(message = "userId không được để trống")
    private String userId;

    @NotBlank(message = "orderId không được để trống")
    private String orderId;

    @NotNull(message = "orderAmount không được để trống")
    private BigDecimal orderAmount;
}
