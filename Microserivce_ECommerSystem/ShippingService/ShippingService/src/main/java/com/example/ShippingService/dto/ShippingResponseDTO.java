package com.example.ShippingService.dto;

import com.example.ShippingService.entity.ShippingStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ShippingResponseDTO {
    private String id;
    private String orderId;
    private String trackingNumber;
    private String carrier;
    private ShippingStatus status;
    private LocalDateTime estimatedDeliveryDate;
    private LocalDateTime actualDeliveryDate;
    private String recipientName;
    private String recipientAddress;
    private String recipientPhone;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
