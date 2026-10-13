package com.example.ChatService.repository;

import com.example.ChatService.entity.ChatConversation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatConversationRepository extends MongoRepository<ChatConversation, String> {

    List<ChatConversation> findAllByOrderByLastMessageAtDesc();
}
