package com.example.OrderService.client;

import com.example.OrderService.dto.ShippingRequestDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "shipping-service", path = "/api/shipping")
public interface ShippingClient {

    @PostMapping
    Object createShipping(@RequestBody ShippingRequestDTO request);

    @org.springframework.web.bind.annotation.GetMapping("/order/{orderId}")
    Object getByOrderId(@org.springframework.web.bind.annotation.PathVariable("orderId") String orderId);
}
