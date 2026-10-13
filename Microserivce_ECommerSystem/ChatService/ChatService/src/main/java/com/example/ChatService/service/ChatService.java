package com.example.ChatService.service;

import com.example.ChatService.entity.ChatConversation;
import com.example.ChatService.entity.ChatMessage;
import com.example.ChatService.repository.ChatConversationRepository;
import com.example.ChatService.repository.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    // Id đại diện cho bộ phận hỗ trợ: mọi ADMIN trả lời chung dưới danh nghĩa này
    public static final String SUPPORT_ID = "admin-support";

    // Kênh chung của nhân viên hỗ trợ — nhận mọi tin của mọi cuộc hội thoại (chỉ ADMIN subscribe được)
    public static final String SUPPORT_TOPIC = "/topic/support";

    // Hàng đợi riêng của từng người dùng; client subscribe "/user/queue/messages"
    public static final String USER_QUEUE = "/queue/messages";

    public static final int MAX_CONTENT_LENGTH = 2000;

    private static final String STATUS_DELIVERED = "DELIVERED";
    private static final String STATUS_READ = "READ";

    private final ChatMessageRepository chatMessageRepository;
    private final ChatConversationRepository conversationRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final MongoTemplate mongoTemplate;

    // ─────────────────────────────────────────────
    // GỬI TIN
    // ─────────────────────────────────────────────

    /** Khách hàng nhắn cho bộ phận hỗ trợ. */
    public ChatMessage sendFromCustomer(String customerId, String customerName, String content) {
        ChatMessage saved = save(customerId, SUPPORT_ID, customerName, content);

        ChatConversation conversation = getOrCreateConversation(customerId);
        conversation.setCustomerName(customerName);
        conversation.setUnreadForSupport(conversation.getUnreadForSupport() + 1);
        touch(conversation, saved);

        deliver(customerId, saved);
        return saved;
    }

    /** Nhân viên hỗ trợ (ADMIN) trả lời một khách hàng. */
    public ChatMessage sendFromSupport(String customerId, String agentName, String content) {
        ChatMessage saved = save(SUPPORT_ID, customerId, agentName, content);

        ChatConversation conversation = getOrCreateConversation(customerId);
        conversation.setUnreadForCustomer(conversation.getUnreadForCustomer() + 1);
        // Đã trả lời nghĩa là đã đọc các tin trước đó của khách
        conversation.setUnreadForSupport(0);
        touch(conversation, saved);

        deliver(customerId, saved);
        return saved;
    }

    private ChatMessage save(String senderId, String recipientId, String senderName, String content) {
        return chatMessageRepository.save(ChatMessage.builder()
                .senderId(senderId)
                .recipientId(recipientId)
                .senderName(senderName)
                .content(content)
                .timestamp(LocalDateTime.now())
                .status(STATUS_DELIVERED)
                .build());
    }

    private void touch(ChatConversation conversation, ChatMessage message) {
        conversation.setLastMessage(message.getContent());
        conversation.setLastSenderId(message.getSenderId());
        conversation.setLastMessageAt(message.getTimestamp());
        conversationRepository.save(conversation);
    }

    // Mỗi tin đi tới 2 nơi: mọi tab đang mở của khách, và kênh chung của các nhân viên hỗ trợ
    private void deliver(String customerId, ChatMessage message) {
        messagingTemplate.convertAndSendToUser(customerId, USER_QUEUE, message);
        messagingTemplate.convertAndSend(SUPPORT_TOPIC, message);
    }

    // ─────────────────────────────────────────────
    // TRA CỨU
    // ─────────────────────────────────────────────

    public List<ChatMessage> getHistory(String customerId) {
        return chatMessageRepository
                .findBySenderIdAndRecipientIdOrSenderIdAndRecipientIdOrderByTimestampAsc(
                        customerId, SUPPORT_ID, SUPPORT_ID, customerId);
    }

    public List<ChatConversation> getConversations() {
        return conversationRepository.findAllByOrderByLastMessageAtDesc();
    }

    public int getUnreadForCustomer(String customerId) {
        return conversationRepository.findById(customerId)
                .map(ChatConversation::getUnreadForCustomer)
                .orElse(0);
    }

    public int getUnreadForSupport() {
        return conversationRepository.findAll().stream()
                .mapToInt(ChatConversation::getUnreadForSupport)
                .sum();
    }

    // ─────────────────────────────────────────────
    // ĐÁNH DẤU ĐÃ ĐỌC
    // ─────────────────────────────────────────────

    /** Khách đã mở khung chat -> các tin của bộ phận hỗ trợ được coi là đã đọc. */
    public void markReadByCustomer(String customerId) {
        markMessagesRead(SUPPORT_ID, customerId);
        conversationRepository.findById(customerId).ifPresent(conversation -> {
            conversation.setUnreadForCustomer(0);
            conversationRepository.save(conversation);
        });
    }

    /** Nhân viên hỗ trợ đã mở hội thoại -> các tin của khách được coi là đã đọc. */
    public void markReadBySupport(String customerId) {
        markMessagesRead(customerId, SUPPORT_ID);
        conversationRepository.findById(customerId).ifPresent(conversation -> {
            conversation.setUnreadForSupport(0);
            conversationRepository.save(conversation);
        });
    }

    private void markMessagesRead(String senderId, String recipientId) {
        Query query = new Query(Criteria.where("senderId").is(senderId)
                .and("recipientId").is(recipientId)
                .and("status").ne(STATUS_READ));
        mongoTemplate.updateMulti(query, Update.update("status", STATUS_READ), ChatMessage.class);
    }

    private ChatConversation getOrCreateConversation(String customerId) {
        return conversationRepository.findById(customerId)
                .orElseGet(() -> ChatConversation.builder().customerId(customerId).build());
    }
}
