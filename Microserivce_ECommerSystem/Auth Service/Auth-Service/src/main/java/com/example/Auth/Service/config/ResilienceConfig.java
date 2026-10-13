package com.example.Auth.Service.config;

import feign.Request;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Giới hạn thời gian chờ khi gọi service khác, để một service chết/treo không kéo treo service này.
 * (Ngắt mạch và thử lại cấu hình ở khối "resilience4j" trong file cấu hình.)
 */
@Configuration
public class ResilienceConfig {

    /** Áp dụng cho mọi Feign client: 3 giây để kết nối, 10 giây chờ phản hồi (mặc định của Feign là 10s / 60s). */
    @Bean
    public Request.Options feignRequestOptions() {
        return new Request.Options(3, TimeUnit.SECONDS, 10, TimeUnit.SECONDS, true);
    }
}
