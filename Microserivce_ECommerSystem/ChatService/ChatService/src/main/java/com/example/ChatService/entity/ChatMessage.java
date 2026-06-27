package com.example.ChatService.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "messages")
public class ChatMessage {

    @Id
    private String id;
    
    private String senderId;
    private String recipientId; // Can be a UserId or ShopId
    
    private String content;
    
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
    
    // Status can be DELIVERED, READ, etc.
    @Builder.Default
    private String status = "SENT";
}
