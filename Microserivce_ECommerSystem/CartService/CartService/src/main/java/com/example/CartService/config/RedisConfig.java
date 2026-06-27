package com.example.CartService.config;

import com.example.CartService.model.Cart;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.*;

@Configuration
public class RedisConfig {

    /**
     * RedisTemplate<String, Cart>
     * Key   → StringRedisSerializer  (dễ đọc trong Redis CLI)
     * Value → Jackson JSON           (dễ debug, không phụ thuộc class version)
     */
    @Bean
    public RedisTemplate<String, Cart> cartRedisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Cart> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        // Key serializer
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        // Value serializer — JSON
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // Bỏ qua field không biết khi deserialize (tránh lỗi khi thêm field mới)
        mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        Jackson2JsonRedisSerializer<Cart> serializer =
                new Jackson2JsonRedisSerializer<>(mapper, Cart.class);

        template.setValueSerializer(serializer);
        template.setHashValueSerializer(serializer);
        template.afterPropertiesSet();
        return template;
    }
}
