package com.example.OrderService.client;

import com.example.OrderService.dto.CouponDiscountResponseDTO;
import com.example.OrderService.dto.CouponRedeemRequestDTO;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "coupon-service", path = "/api/coupons")
// Ngắt mạch: service này lỗi liên tục thì tạm ngừng gọi, trả lỗi ngay thay vì chờ hết timeout
@CircuitBreaker(name = "couponService")
public interface CouponClient {

    // Cả hai lời gọi đều tự chống trùng theo orderId ở Coupon Service nên thử lại an toàn
    @Retry(name = "couponService")
    @PostMapping("/redeem")
    CouponDiscountResponseDTO redeem(@RequestBody CouponRedeemRequestDTO request);

    @Retry(name = "couponService")
    @PostMapping("/{orderId}/reverse")
    void reverse(@PathVariable("orderId") String orderId);
}
