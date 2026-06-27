package com.example.OrderService.config;

import feign.RequestInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
public class FeignAuthConfig {

    @Bean
    public RequestInterceptor authForwardingInterceptor() {
        return requestTemplate -> {
            RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
            if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
                return;
            }

            HttpServletRequest currentRequest = servletAttributes.getRequest();
            String authorizationHeader = currentRequest.getHeader("Authorization");
            if (authorizationHeader != null && !authorizationHeader.isBlank()) {
                requestTemplate.header("Authorization", authorizationHeader);
            }
        };
    }
}
