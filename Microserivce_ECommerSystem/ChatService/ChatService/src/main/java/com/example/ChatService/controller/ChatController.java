package com.example.ChatService.controller;

import com.example.ChatService.entity.ChatMessage;
import com.example.ChatService.repository.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/chat")
public class ChatController {

    private final SimpMessagingTemplate messagingTemplate;
    private final ChatMessageRepository chatMessageRepository;

    /**
     * WebSocket Endpoint to receive messages
     * Clients will send to: /app/chat.sendMessage
     */
    @MessageMapping("/chat.sendMessage")
    public void processMessage(@Payload ChatMessage chatMessage) {
        log.info("Received WebSocket message from {} to {}: {}", chatMessage.getSenderId(), chatMessage.getRecipientId(), chatMessage.getContent());
        
        chatMessage.setTimestamp(LocalDateTime.now());
        chatMessage.setStatus("DELIVERED");

        // 1. Save to MongoDB
        ChatMessage savedMsg = chatMessageRepository.save(chatMessage);

        // 2. Broadcast to Recipient's Queue
        // Subscribed clients listen to: /user/{userId}/queue/messages
        messagingTemplate.convertAndSendToUser(
                chatMessage.getRecipientId(), "/queue/messages",
                savedMsg
        );
        
        // Broadcast back to Sender to confirm delivery
        messagingTemplate.convertAndSendToUser(
                chatMessage.getSenderId(), "/queue/messages",
                savedMsg
        );
    }

    /**
     * REST API to fetch chat history between two users
     */
    @GetMapping("/messages/{senderId}/{recipientId}")
    public ResponseEntity<List<ChatMessage>> getChatHistory(
            @PathVariable String senderId,
            @PathVariable String recipientId) {
        
        List<ChatMessage> history = chatMessageRepository
                .findBySenderIdAndRecipientIdOrSenderIdAndRecipientIdOrderByTimestampAsc(
                        senderId, recipientId, recipientId, senderId);
                        
        return ResponseEntity.ok(history);
    }
}
