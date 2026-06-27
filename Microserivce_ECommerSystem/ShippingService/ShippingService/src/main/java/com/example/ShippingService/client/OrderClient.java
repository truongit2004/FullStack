package com.example.ShippingService.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;

@FeignClient(name = "order-service", path = "/api/orders")
public interface OrderClient {

    @PutMapping("/{id}/ship")
    Object shipOrder(@PathVariable("id") String id);

    @PutMapping("/{id}/deliver")
    Object deliverOrder(@PathVariable("id") String id);

    @PutMapping("/{id}/cancel")
    Object cancelOrder(@PathVariable("id") String id);

    @PutMapping("/{id}/return")
    Object returnOrder(@PathVariable("id") String id);

    @org.springframework.web.bind.annotation.GetMapping("/{id}")
    com.example.ShippingService.dto.ExternalOrderResponseDTO getOrderById(@PathVariable("id") String id);
}
