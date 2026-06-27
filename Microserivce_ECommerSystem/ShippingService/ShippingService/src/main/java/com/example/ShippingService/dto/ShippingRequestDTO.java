package com.example.ShippingService.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ShippingRequestDTO {

    @NotBlank(message = "Order ID is required")
    private String orderId;

    @NotBlank(message = "Carrier is required")
    private String carrier;

    @NotBlank(message = "Recipient name is required")
    private String recipientName;

    @NotBlank(message = "Recipient address is required")
    private String recipientAddress;

    @NotBlank(message = "Recipient phone is required")
    private String recipientPhone;
}
