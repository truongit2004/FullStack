package com.example.ChatService.controller;

import com.example.ChatService.dto.ChatSendRequest;
import com.example.ChatService.entity.ChatConversation;
import com.example.ChatService.entity.ChatMessage;
import com.example.ChatService.security.StompPrincipal;
import com.example.ChatService.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    /**
     * WebSocket Endpoint to receive messages
     * Clients will send to: /app/chat.sendMessage
     *
     * Người gửi lấy từ phiên STOMP đã xác thực (xem StompAuthChannelInterceptor):
     * - Khách hàng: tin luôn đi tới bộ phận hỗ trợ.
     * - ADMIN: trả lời khách có id = recipientId, dưới danh nghĩa bộ phận hỗ trợ.
     */
    @MessageMapping("/chat.sendMessage")
    public void processMessage(@Payload ChatSendRequest request, Principal principal) {
        if (!(principal instanceof StompPrincipal sender)) {
            log.warn("Bỏ qua tin nhắn từ phiên chưa xác thực");
            return;
        }

        String content = request.getContent() == null ? "" : request.getContent().trim();
        if (content.isEmpty()) {
            return;
        }
        if (content.length() > ChatService.MAX_CONTENT_LENGTH) {
            content = content.substring(0, ChatService.MAX_CONTENT_LENGTH);
        }

        if (sender.isAdmin()) {
            String customerId = request.getRecipientId();
            if (customerId == null || customerId.isBlank() || ChatService.SUPPORT_ID.equals(customerId)) {
                log.warn("ADMIN {} gửi tin nhưng thiếu recipientId hợp lệ", sender.username());
                return;
            }
            chatService.sendFromSupport(customerId, sender.username(), content);
        } else {
            chatService.sendFromCustomer(sender.userId(), sender.username(), content);
        }
    }

    /**
     * REST API to fetch chat history between two users
     * (một trong hai id là khách hàng, id còn lại là "admin-support").
     * Chỉ chính khách hàng đó hoặc ADMIN được xem.
     */
    @GetMapping("/messages/{senderId}/{recipientId}")
    public ResponseEntity<List<ChatMessage>> getChatHistory(
            @PathVariable String senderId,
            @PathVariable String recipientId,
            @RequestHeader(value = "X-User-Id", required = false) String authId,
            @RequestHeader(value = "X-User-Role", required = false) String authRole) {

        String customerId = ChatService.SUPPORT_ID.equals(senderId) ? recipientId : senderId;
        requireOwnerOrAdmin(customerId, authId, authRole);
        return ResponseEntity.ok(chatService.getHistory(customerId));
    }

    /** Danh sách hội thoại cho màn hình hỗ trợ, mới nhất trước. Chỉ ADMIN. */
    @GetMapping("/conversations")
    public ResponseEntity<List<ChatConversation>> getConversations(
            @RequestHeader(value = "X-User-Role", required = false) String authRole) {
        requireAdmin(authRole);
        return ResponseEntity.ok(chatService.getConversations());
    }

    /** Đánh dấu đã đọc: khách đọc tin của hỗ trợ, hoặc ADMIN đọc tin của khách. */
    @PutMapping("/conversations/{customerId}/read")
    public ResponseEntity<Void> markRead(
            @PathVariable String customerId,
            @RequestHeader(value = "X-User-Id", required = false) String authId,
            @RequestHeader(value = "X-User-Role", required = false) String authRole) {

        if (isAdmin(authRole)) {
            chatService.markReadBySupport(customerId);
        } else {
            requireOwnerOrAdmin(customerId, authId, authRole);
            chatService.markReadByCustomer(customerId);
        }
        return ResponseEntity.ok().build();
    }

    /** Số tin chưa đọc của người đang đăng nhập (để hiện huy hiệu trên nút chat). */
    @GetMapping("/unread")
    public ResponseEntity<Map<String, Integer>> getUnread(
            @RequestHeader(value = "X-User-Id", required = false) String authId,
            @RequestHeader(value = "X-User-Role", required = false) String authRole) {

        if (authId == null || authId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        int unread = isAdmin(authRole)
                ? chatService.getUnreadForSupport()
                : chatService.getUnreadForCustomer(authId);
        return ResponseEntity.ok(Map.of("unread", unread));
    }

    // ── Private helpers ─────────────────────────────────────────────────────
    // Danh tính lấy từ header X-User-* do API Gateway gắn sau khi kiểm tra JWT.

    private boolean isAdmin(String role) {
        return "ADMIN".equalsIgnoreCase(role);
    }

    private void requireAdmin(String role) {
        if (!isAdmin(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    private void requireOwnerOrAdmin(String customerId, String authId, String authRole) {
        if (isAdmin(authRole)) {
            return;
        }
        if (authId == null || authId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        if (!authId.equals(customerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }
}
