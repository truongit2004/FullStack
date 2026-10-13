package com.example.ChatService.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Tóm tắt 1 cuộc hội thoại giữa 1 khách hàng và bộ phận hỗ trợ — mỗi khách đúng 1 dòng,
 * cập nhật mỗi khi có tin nhắn mới, để màn hình Admin liệt kê nhanh mà không phải quét
 * toàn bộ collection "messages".
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "conversations")
public class ChatConversation {

    @Id
    private String customerId;

    private String customerName;

    private String lastMessage;
    private String lastSenderId;
    private LocalDateTime lastMessageAt;

    // Số tin khách gửi mà bộ phận hỗ trợ chưa đọc
    private int unreadForSupport;

    // Số tin bộ phận hỗ trợ gửi mà khách chưa đọc
    private int unreadForCustomer;
}
