package com.example.OrderService.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ShippingRequestDTO {
    private String orderId;
    private String carrier;
    private String recipientName;
    private String recipientAddress;
    private String recipientPhone;
}
