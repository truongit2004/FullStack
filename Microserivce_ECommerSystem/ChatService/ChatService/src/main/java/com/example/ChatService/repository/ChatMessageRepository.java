package com.example.ChatService.repository;

import com.example.ChatService.entity.ChatMessage;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends MongoRepository<ChatMessage, String> {
    // Find conversation between sender and recipient
    List<ChatMessage> findBySenderIdAndRecipientIdOrSenderIdAndRecipientIdOrderByTimestampAsc(
            String senderId1, String recipientId1,
            String senderId2, String recipientId2
    );
}
