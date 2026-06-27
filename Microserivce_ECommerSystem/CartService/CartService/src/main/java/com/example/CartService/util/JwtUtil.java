package com.example.CartService.util;

import com.example.CartService.exception.CartException;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;

@Slf4j
@Component
public class JwtUtil {

    private final Key signingKey;
    private final String userIdClaim;

    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.user-id-claim:sub}") String userIdClaim) {
        byte[] keyBytes = hexToBytes(secret); // ← HEX decode giống Product Service
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.userIdClaim = userIdClaim;
    }

    // Decode HEX string → byte[]
    private byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    public String extractUserId(String token) {
        Claims claims = parseClaims(token);
        String userId = claims.get(userIdClaim, String.class);
        if (userId == null) {
            userId = claims.getSubject();
        }
        if (userId == null) {
            throw new CartException("Không thể lấy userId từ token.");
        }
        return userId;
    }

    public boolean isTokenValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(signingKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (ExpiredJwtException e) {
            throw new CartException("Token đã hết hạn.");
        } catch (JwtException e) {
            throw new CartException("Token không hợp lệ.");
        }
    }
}