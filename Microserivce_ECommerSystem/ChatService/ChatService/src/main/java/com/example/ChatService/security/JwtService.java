package com.example.ChatService.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    /**
     * Kiểm tra chữ ký + hạn dùng và trả về danh tính trong token.
     * Ném exception nếu token không hợp lệ.
     */
    public StompPrincipal parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSignKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        Object userId = claims.get("userId");
        if (userId == null) {
            throw new IllegalArgumentException("Token không chứa userId");
        }
        return new StompPrincipal(String.valueOf(userId), claims.getSubject(), claims.get("role", String.class));
    }

    private SecretKey getSignKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
