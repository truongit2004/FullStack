package com.example.NotificationService.controller;

import com.example.NotificationService.dto.NotificationEvent;
import com.example.NotificationService.dto.PasswordResetEmailRequest;
import com.example.NotificationService.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final EmailService emailService;

    @PostMapping("/send-test")
    public ResponseEntity<String> sendTestEmail(@RequestBody NotificationEvent event) {
        try {
            if ("ORDER_PLACED".equalsIgnoreCase(event.getType())) {
                emailService.sendOrderPlacedEmail(event);
            } else {
                emailService.sendPaymentSuccessEmail(event);
            }
            return ResponseEntity.ok("Email sent successfully (" + event.getType() + ")!");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Failed to send email: " + e.getMessage());
        }
    }

    @PostMapping("/send-password-reset")
    public ResponseEntity<String> sendPasswordReset(@RequestBody PasswordResetEmailRequest request) {
        try {
            emailService.sendPasswordResetEmail(request);
            return ResponseEntity.ok("Password reset email sent successfully!");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Failed to send email: " + e.getMessage());
        }
    }
}
