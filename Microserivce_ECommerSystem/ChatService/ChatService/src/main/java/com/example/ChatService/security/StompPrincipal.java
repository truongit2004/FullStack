package com.example.ChatService.security;

import java.security.Principal;

/**
 * Danh tính gắn vào phiên STOMP sau khi xác thực JWT ở frame CONNECT.
 * getName() trả về userId để dùng với convertAndSendToUser(userId, ...).
 */
public record StompPrincipal(String userId, String username, String role) implements Principal {

    @Override
    public String getName() {
        return userId;
    }

    public boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(role);
    }
}
