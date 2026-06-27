package com.example.CartService.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import com.example.CartService.filter.GatewayHeaderAuthFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final GatewayHeaderAuthFilter gatewayHeaderAuthFilter;

    public SecurityConfig(GatewayHeaderAuthFilter gatewayHeaderAuthFilter) {
        this.gatewayHeaderAuthFilter = gatewayHeaderAuthFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(gatewayHeaderAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        // Health check / Eureka probe endpoints
                        .requestMatchers("/actuator/**").permitAll()
                        // Internal endpoint cho OrderService gọi để xóa cart sau checkout
                        .requestMatchers(HttpMethod.DELETE, "/api/cart/{targetUserId}").permitAll()
                        // Tất cả cart API còn lại phải đi qua Gateway (có X-User-Id header)
                        .anyRequest().authenticated()
                );

        return http.build();
    }
}