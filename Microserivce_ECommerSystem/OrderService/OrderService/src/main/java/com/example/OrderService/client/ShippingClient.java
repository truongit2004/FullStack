package com.example.OrderService.client;

import com.example.OrderService.dto.ShippingFeeResponseDTO;
import com.example.OrderService.dto.ShippingRequestDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

@FeignClient(name = "shipping-service", path = "/api/shipping")
// Ngắt mạch: service này lỗi liên tục thì tạm ngừng gọi, trả lỗi ngay thay vì chờ hết timeout
@CircuitBreaker(name = "shippingService")
public interface ShippingClient {

    @PostMapping
    Object createShipping(@RequestBody ShippingRequestDTO request);

    @Retry(name = "shippingService")
    @org.springframework.web.bind.annotation.GetMapping("/order/{orderId}")
    Object getByOrderId(@org.springframework.web.bind.annotation.PathVariable("orderId") String orderId);

    @Retry(name = "shippingService")
    @org.springframework.web.bind.annotation.GetMapping("/fee")
    ShippingFeeResponseDTO getShippingFee(@RequestParam("address") String address);
}
