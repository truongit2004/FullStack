package com.example.ReviewService.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "ORDER-SERVICE")
public interface OrderClient {

    @GetMapping("/api/orders/check-purchased")
    Boolean checkPurchased(@RequestParam("userId") String userId, @RequestParam("productId") String productId);
}
