package com.example.ChatService.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Prefix for server-to-client subscriptions (e.g. /topic/support, /user/queue/messages)
        config.enableSimpleBroker("/topic", "/queue");

        // Prefix for client-to-server messages (e.g. /app/chat.send)
        config.setApplicationDestinationPrefixes("/app");

        // Client subscribe "/user/queue/messages" -> chỉ nhận tin gửi riêng cho chính mình
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Endpoint dành cho Postman và các client chuẩn (không dùng SockJS)
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");

        // Endpoint dành cho trình duyệt/ReactJS (có dùng SockJS làm fallback)
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}
