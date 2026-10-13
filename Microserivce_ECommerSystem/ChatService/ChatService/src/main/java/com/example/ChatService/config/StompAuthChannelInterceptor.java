package com.example.ChatService.config;

import com.example.ChatService.security.JwtService;
import com.example.ChatService.security.StompPrincipal;
import com.example.ChatService.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * Xác thực ở tầng STOMP: trình duyệt không gửi được header Authorization khi bắt tay
 * WebSocket/SockJS, nên JWT được gửi trong frame CONNECT và kiểm tra tại đây.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String header = accessor.getFirstNativeHeader("Authorization");
            if (header == null || !header.startsWith("Bearer ")) {
                throw new MessageDeliveryException("Thiếu token đăng nhập");
            }
            try {
                accessor.setUser(jwtService.parse(header.substring(7).trim()));
            } catch (Exception e) {
                log.warn("Từ chối kết nối chat: token không hợp lệ ({})", e.getMessage());
                throw new MessageDeliveryException("Token không hợp lệ hoặc đã hết hạn");
            }
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            // Kênh chung của nhân viên hỗ trợ: chỉ ADMIN được nghe
            String destination = accessor.getDestination();
            if (destination != null && destination.startsWith(ChatService.SUPPORT_TOPIC)
                    && !(accessor.getUser() instanceof StompPrincipal principal && principal.isAdmin())) {
                throw new MessageDeliveryException("Không có quyền nghe kênh hỗ trợ");
            }
        }
        return message;
    }
}
