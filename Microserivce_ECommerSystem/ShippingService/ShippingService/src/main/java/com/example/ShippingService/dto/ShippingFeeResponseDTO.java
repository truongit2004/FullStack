package com.example.ShippingService.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ShippingFeeResponseDTO {
    private BigDecimal fee;
    private String zone;
}
