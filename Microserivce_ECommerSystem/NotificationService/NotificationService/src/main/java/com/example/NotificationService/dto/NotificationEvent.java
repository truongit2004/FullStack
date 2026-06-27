package com.example.NotificationService.dto;

import lombok.*;

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
