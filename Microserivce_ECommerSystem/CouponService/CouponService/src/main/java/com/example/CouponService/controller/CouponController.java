package com.example.CouponService.controller;

import com.example.CouponService.dto.CouponDiscountResponseDTO;
import com.example.CouponService.dto.CouponRedeemRequestDTO;
import com.example.CouponService.dto.CouponRequestDTO;
import com.example.CouponService.dto.CouponResponseDTO;
import com.example.CouponService.service.CouponService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Phân quyền nằm ở API Gateway (JwtGatewayFilter):
 *  - /preview, /available : khách đã đăng nhập
 *  - /redeem, /{orderId}/reverse : NỘI BỘ, chỉ Order Service gọi thẳng; Gateway chặn từ bên ngoài
 *  - còn lại : ADMIN
 */
@RestController
@RequestMapping("/api/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    // ── Khách hàng ───────────────────────────────────────────────────────────

    @GetMapping("/preview")
    public ResponseEntity<CouponDiscountResponseDTO> preview(
            @RequestParam String code,
            @RequestParam BigDecimal orderAmount,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        return ResponseEntity.ok(couponService.preview(code, userId, orderAmount));
    }

    @GetMapping("/available")
    public ResponseEntity<List<CouponResponseDTO>> getAvailable() {
        return ResponseEntity.ok(couponService.getAvailable());
    }

    // ── Nội bộ (Order Service) ───────────────────────────────────────────────

    @PostMapping("/redeem")
    public ResponseEntity<CouponDiscountResponseDTO> redeem(@Valid @RequestBody CouponRedeemRequestDTO request) {
        return ResponseEntity.ok(couponService.redeem(request));
    }

    @PostMapping("/{orderId}/reverse")
    public ResponseEntity<Void> reverse(@PathVariable String orderId) {
        couponService.reverseForOrder(orderId);
        return ResponseEntity.ok().build();
    }

    // ── Quản trị ─────────────────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<List<CouponResponseDTO>> getAll() {
        return ResponseEntity.ok(couponService.getAll());
    }

    @PostMapping
    public ResponseEntity<CouponResponseDTO> create(@Valid @RequestBody CouponRequestDTO request) {
        return ResponseEntity.ok(couponService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CouponResponseDTO> update(@PathVariable String id, @Valid @RequestBody CouponRequestDTO request) {
        return ResponseEntity.ok(couponService.update(id, request));
    }

    @PutMapping("/{id}/active")
    public ResponseEntity<CouponResponseDTO> setActive(@PathVariable String id, @RequestParam boolean value) {
        return ResponseEntity.ok(couponService.setActive(id, value));
    }
}
