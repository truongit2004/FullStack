package com.example.Auth.Service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetEmailRequest {
    private String email;
    private String customerName;
    private String otp;
}
