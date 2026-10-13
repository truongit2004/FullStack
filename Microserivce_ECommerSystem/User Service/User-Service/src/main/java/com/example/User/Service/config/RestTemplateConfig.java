package com.example.User.Service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        // Giới hạn thời gian chờ: Order/Product Service chết hay treo thì trang hồ sơ báo lỗi nhanh,
        // không giữ luồng xử lý vô thời hạn (mặc định RestTemplate không có timeout).
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3_000);
        factory.setReadTimeout(10_000);
        return new RestTemplate(factory);
    }
}
