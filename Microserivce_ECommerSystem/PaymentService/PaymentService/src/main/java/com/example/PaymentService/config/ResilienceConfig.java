package com.example.PaymentService.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import feign.Request;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.kafka.DefaultKafkaProducerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Giới hạn thời gian chờ khi gọi ra ngoài và cách xử lý khi tiêu thụ tin Kafka bị lỗi.
 * (Ngắt mạch và thử lại cho Feign cấu hình ở khối "resilience4j" trong application.yml.)
 */
@Configuration
@EnableScheduling   // cho PaymentCompletionService.retryUnsyncedPayments
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

    /**
     * Tin Kafka xử lý lỗi: thử lại 8 lần, giãn dần 2s -> 60s (tổng khoảng 4 phút), đủ để vượt qua
     * lúc database hay service phụ thuộc chập chờn. Vẫn lỗi thì chuyển tin sang topic "<tên topic>.DLT"
     * để không mất và không chặn các tin phía sau. Tin sai định dạng JSON thì chuyển thẳng, không thử lại.
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<?, ?> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, exception) -> new TopicPartition(record.topic() + ".DLT", -1));

        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(8);
        backOff.setInitialInterval(2_000L);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(60_000L);

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(JsonProcessingException.class);
        return handler;
    }
}
