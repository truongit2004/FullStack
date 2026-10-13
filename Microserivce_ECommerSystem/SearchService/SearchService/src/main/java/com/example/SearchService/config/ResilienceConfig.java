package com.example.SearchService.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

/**
 * Cách xử lý khi tiêu thụ tin Kafka bị lỗi.
 */
@Configuration
public class ResilienceConfig {

    /**
     * Tin Kafka xử lý lỗi: thử lại 8 lần, giãn dần 2s -> 60s (tổng khoảng 4 phút), đủ để vượt qua
     * lúc database hay service phụ thuộc chập chờn. Vẫn lỗi thì chuyển tin sang topic "<tên topic>.DLT"
     * để không mất và không chặn các tin phía sau. Tin sai định dạng JSON thì chuyển thẳng, không thử lại.
     * (Trước đây consumer bắt mọi lỗi rồi chỉ ghi log, tin lỗi bị mất vĩnh viễn.)
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
