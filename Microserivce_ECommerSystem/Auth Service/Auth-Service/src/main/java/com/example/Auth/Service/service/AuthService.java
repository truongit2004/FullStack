package com.example.Auth.Service.service;

import com.example.Auth.Service.client.NotificationClient;
import com.example.Auth.Service.client.UserClient;
import com.example.Auth.Service.dto.AuthRequest;
import com.example.Auth.Service.dto.AuthResponse;
import com.example.Auth.Service.dto.ChangePasswordRequest;
import com.example.Auth.Service.dto.PasswordResetEmailRequest;
import com.example.Auth.Service.dto.ResetPasswordRequest;
import com.example.Auth.Service.dto.UpdatePasswordRequest;
import com.example.Auth.Service.dto.UserDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Date;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String BLACKLIST_PREFIX = "blacklist:";
    private static final String RESET_OTP_PREFIX = "pwreset:";
    private static final long RESET_OTP_TTL_SECONDS = 600; // 10 phút

    private final UserClient userClient;
    private final NotificationClient notificationClient;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final StringRedisTemplate redisTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthResponse register(UserDto request) {
        userClient.createUser(request);
        return new AuthResponse(null, "User registered successfully. Please login.");
    }

    public AuthResponse login(AuthRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );
        UserDto user = userClient.getUserByUsername(request.getUsername());
        String token = jwtService.generateToken(user.getId(), user.getUsername(), user.getRole());
        return new AuthResponse(token, "User logged in successfully");
    }

    /**
     * Kiểm tra token có hợp lệ không:
     * 1. Không nằm trong Redis blacklist (đã logout)
     * 2. Chữ ký JWT và hạn sử dụng còn hợp lệ
     */
    public void validateToken(String token) {
        checkBlacklist(token);
        jwtService.validateToken(token);
    }

    public void logout(String authHeader) {
        String token = extractBearerToken(authHeader);
        jwtService.validateToken(token);

        long ttl = getRemainingTtl(token);
        addToBlacklist(token, ttl);
        log.info("Token revoked successfully (added to blacklist).");
    }

    /**
     * Gửi OTP đặt lại mật khẩu qua email. Không tiết lộ email có tồn tại hay
     * không (luôn trả về thành công) để tránh dò quét danh sách tài khoản.
     */
    public void forgotPassword(String email) {
        UserDto user;
        try {
            user = userClient.getUserByEmail(email);
        } catch (Exception e) {
            log.warn("Forgot-password requested for unknown email: {}", email);
            return;
        }
        if (user == null) {
            return;
        }

        String otp = generateOtp();
        redisTemplate.opsForValue().set(otpKey(email), otp, RESET_OTP_TTL_SECONDS, TimeUnit.SECONDS);

        try {
            notificationClient.sendPasswordReset(new PasswordResetEmailRequest(email, user.getUsername(), otp));
        } catch (Exception e) {
            log.error("Failed to send password reset email to {}: {}", email, e.getMessage());
        }
    }

    public void resetPassword(ResetPasswordRequest request) {
        String key = otpKey(request.getEmail());
        String storedOtp = redisTemplate.opsForValue().get(key);
        if (storedOtp == null || !storedOtp.equals(request.getOtp())) {
            throw new RuntimeException("Invalid or expired OTP");
        }
        if (request.getNewPassword() == null || request.getNewPassword().isBlank()) {
            throw new RuntimeException("New password must not be blank");
        }

        UserDto user = userClient.getUserByEmail(request.getEmail());
        userClient.updatePassword(user.getId(), new UpdatePasswordRequest(request.getNewPassword()));
        redisTemplate.delete(key);
        log.info("Password reset successfully for email: {}", request.getEmail());
    }

    public void changePassword(String authHeader, ChangePasswordRequest request) {
        String token = extractBearerToken(authHeader);
        validateToken(token);

        String username = jwtService.extractUsername(token);

        // Xác thực lại mật khẩu cũ trước khi cho phép đổi
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, request.getOldPassword()));

        UserDto user = userClient.getUserByUsername(username);
        userClient.updatePassword(user.getId(), new UpdatePasswordRequest(request.getNewPassword()));
        log.info("Password changed successfully for username: {}", username);
    }

    private String generateOtp() {
        return String.format("%06d", secureRandom.nextInt(1_000_000));
    }

    private String otpKey(String email) {
        return RESET_OTP_PREFIX + email.trim().toLowerCase();
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private void checkBlacklist(String token) {
        try {
            if (Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + token))) {
                throw new RuntimeException("Token has been revoked (logged out)");
            }
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            // Redis unavailable — fail secure: block all requests
            log.error("Redis unavailable during token blacklist check: {}", ex.getMessage());
            throw new RuntimeException("Cannot validate token state. Please try again.");
        }
    }

    private long getRemainingTtl(String token) {
        Date expiration = jwtService.extractExpiration(token);
        long ttl = expiration.getTime() - System.currentTimeMillis();
        if (ttl <= 0) {
            throw new RuntimeException("Token is already expired");
        }
        return ttl;
    }

    private void addToBlacklist(String token, long ttlMillis) {
        try {
            redisTemplate.opsForValue().set(
                    BLACKLIST_PREFIX + token,
                    "true",
                    ttlMillis,
                    TimeUnit.MILLISECONDS
            );
        } catch (Exception ex) {
            log.error("Failed to write token blacklist to Redis: {}", ex.getMessage());
            throw new RuntimeException("Logout failed: cannot persist token blacklist");
        }
    }

    private String extractBearerToken(String authHeader) {
        if (authHeader == null || authHeader.isBlank()) {
            throw new RuntimeException("Missing Authorization header");
        }
        if (!authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("Authorization header must use Bearer scheme");
        }
        String token = authHeader.substring(7).trim();
        if (token.isEmpty()) {
            throw new RuntimeException("Bearer token is empty");
        }
        return token;
    }
}

