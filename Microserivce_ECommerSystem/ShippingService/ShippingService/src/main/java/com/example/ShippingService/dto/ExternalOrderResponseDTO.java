package com.example.ShippingService.dto;

import lombok.Data;

@Data
public class ExternalOrderResponseDTO {
    private String id;
    private String status; // We'll keep it as String to avoid enum dependency
}
