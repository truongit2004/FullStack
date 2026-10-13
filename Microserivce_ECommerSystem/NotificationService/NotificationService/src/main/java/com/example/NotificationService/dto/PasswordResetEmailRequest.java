package com.example.NotificationService.dto;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PasswordResetEmailRequest {
    private String email;
    private String customerName;
    private String otp;
}
