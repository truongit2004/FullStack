package com.example.PaymentService.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class NotificationEvent {
    private String email;
    private String customerName;
    private String orderId;
    private String amount;
    private String status;
    private String type;
}
