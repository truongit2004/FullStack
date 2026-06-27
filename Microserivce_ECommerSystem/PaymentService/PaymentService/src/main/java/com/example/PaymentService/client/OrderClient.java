package com.example.PaymentService.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(name = "order-service")
public interface OrderClient {

    @GetMapping("/api/orders/{id}")
    Object getOrderById(@PathVariable("id") String id);

    @PostMapping("/api/orders/{id}/paid")
    void markOrderAsPaid(@PathVariable("id") String id);
}
