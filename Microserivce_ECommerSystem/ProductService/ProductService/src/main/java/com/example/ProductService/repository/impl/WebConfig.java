package com.example.ProductService.repository.impl;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Truy cập ảnh qua: GET /uploads/products/filename.jpg
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:uploads/");
    }
}