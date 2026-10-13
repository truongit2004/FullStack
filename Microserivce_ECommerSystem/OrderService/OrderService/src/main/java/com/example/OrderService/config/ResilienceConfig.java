package com.example.OrderService.config;

import feign.Request;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.boot.autoconfigure.kafka.DefaultKafkaProducerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Giới hạn thời gian chờ khi gọi ra ngoài, để một service chết/treo không kéo treo service này.
 * (Ngắt mạch và thử lại cấu hình ở khối "resilience4j" trong application.yaml.)
 */
@Configuration
@EnableScheduling   // cho PendingTaskProcessor
public class ResilienceConfig {

    /** Áp dụng cho mọi Feign client: 3 giây để kết nối, 10 giây chờ phản hồi (mặc định của Feign là 10s / 60s). */
    @Bean
    public Request.Options feignRequestOptions() {
        return new Request.Options(3, TimeUnit.SECONDS, 10, TimeUnit.SECONDS, true);
    }

    /** Kafka chết thì send() chỉ chờ tối đa 5 giây (mặc định 60 giây) rồi báo lỗi. */
    @Bean
    public DefaultKafkaProducerFactoryCustomizer kafkaProducerFailFast() {
        return factory -> factory.updateConfigs(Map.of(ProducerConfig.MAX_BLOCK_MS_CONFIG, 5000));
    }
}
