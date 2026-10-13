package com.example.Auth.Service.controller;

import com.example.Auth.Service.dto.AuthRequest;
import com.example.Auth.Service.dto.AuthResponse;
import com.example.Auth.Service.dto.ChangePasswordRequest;
import com.example.Auth.Service.dto.ForgotPasswordRequest;
import com.example.Auth.Service.dto.ResetPasswordRequest;
import com.example.Auth.Service.dto.UserDto;
import com.example.Auth.Service.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody UserDto request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody AuthRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/validate")
    public ResponseEntity<String> validateToken(@RequestParam("token") String token) {
        authService.validateToken(token);
        return ResponseEntity.ok("Token is valid");
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(@RequestHeader("Authorization") String authorizationHeader) {
        authService.logout(authorizationHeader);
        return ResponseEntity.ok("Logged out successfully. Token has been revoked.");
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.getEmail());
        return ResponseEntity.ok("If the email exists, a reset code has been sent.");
    }

    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok("Password has been reset successfully.");
    }

    @PutMapping("/change-password")
    public ResponseEntity<String> changePassword(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestBody ChangePasswordRequest request) {
        authService.changePassword(authorizationHeader, request);
        return ResponseEntity.ok("Password changed successfully.");
    }
}
