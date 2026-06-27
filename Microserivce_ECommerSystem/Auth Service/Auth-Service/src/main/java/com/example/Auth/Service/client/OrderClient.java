package com.example.Auth.Service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@FeignClient(name = "ORDER-SERVICE", url = "http://localhost:8084")
public interface OrderClient {

    @GetMapping("/api/orders")
    Map<String, Object> getAllOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    );

    @GetMapping("/api/orders/{id}")
    Object getOrderById(@PathVariable("id") String id);

    @GetMapping("/api/orders/user/{userId}")
    Map<String, Object> getOrdersByUser(
            @PathVariable("userId") String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    );
}
