package com.example.ChatService.dto;

import lombok.Data;

/**
 * Payload client gửi tới /app/chat.sendMessage.
 * Người gửi KHÔNG lấy từ payload mà lấy từ phiên STOMP đã xác thực (chống mạo danh).
 */
@Data
public class ChatSendRequest {

    private String content;

    // Chỉ dùng khi người gửi là ADMIN: id khách hàng cần trả lời. Khách gửi thì bỏ qua field này.
    private String recipientId;
}
