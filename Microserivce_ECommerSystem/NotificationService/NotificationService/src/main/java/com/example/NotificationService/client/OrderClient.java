package com.example.NotificationService.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "order-service")
public interface OrderClient {
    @GetMapping("/api/orders/{id}")
    Object getOrderById(@PathVariable("id") String id);
}
