package com.example.Auth.Service.service;

import com.example.Auth.Service.client.UserClient;
import com.example.Auth.Service.dto.AuthRequest;
import com.example.Auth.Service.dto.AuthResponse;
import com.example.Auth.Service.dto.UserDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String BLACKLIST_PREFIX = "blacklist:";

    private final UserClient userClient;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final StringRedisTemplate redisTemplate;

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

