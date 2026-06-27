package com.example.CartService.controller;

import com.example.CartService.dto.CartDTO;
import com.example.CartService.service.CartService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private static final String GATEWAY_USER_ID_HEADER = "X-User-Id";
    private static final String JWT_TOKEN_ATTRIBUTE    = "jwt-token";

    private final CartService cartService;

    @GetMapping
    public ResponseEntity<CartDTO.CartResponse> getCart(
            @AuthenticationPrincipal String principal,
            HttpServletRequest request) {
        return ResponseEntity.ok(cartService.getCart(resolveUserId(request, principal)));
    }

    @PostMapping("/items")
    public ResponseEntity<CartDTO.CartResponse> addItem(
            @AuthenticationPrincipal String principal,
            HttpServletRequest request,
            @Valid @RequestBody CartDTO.AddItemRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(cartService.addItem(resolveUserId(request, principal), body));
    }

    @PutMapping("/items/{productId}")
    public ResponseEntity<CartDTO.CartResponse> updateItem(
            @AuthenticationPrincipal String principal,
            HttpServletRequest request,
            @PathVariable String productId,
            @Valid @RequestBody CartDTO.UpdateItemRequest body) {
        return ResponseEntity.ok(cartService.updateItem(resolveUserId(request, principal), productId, body));
    }

    @DeleteMapping("/items/{productId}")
    public ResponseEntity<CartDTO.CartResponse> removeItem(
            @AuthenticationPrincipal String principal,
            HttpServletRequest request,
            @PathVariable String productId) {
        return ResponseEntity.ok(cartService.removeItem(resolveUserId(request, principal), productId));
    }

    @DeleteMapping("/items")
    public ResponseEntity<CartDTO.CartResponse> removeItems(
            @AuthenticationPrincipal String principal,
            HttpServletRequest request,
            @RequestParam List<String> productIds) {
        return ResponseEntity.ok(cartService.removeItems(resolveUserId(request, principal), productIds));
    }

    @DeleteMapping
    public ResponseEntity<CartDTO.CartResponse> clearCart(
            @AuthenticationPrincipal String principal,
            HttpServletRequest request) {
        return ResponseEntity.ok(cartService.clearCart(resolveUserId(request, principal)));
    }

    /** Internal endpoint: cho phép OrderService / cleanup gọi trực tiếp bằng userId. */
    @DeleteMapping("/{targetUserId}")
    public ResponseEntity<Void> deleteCartById(@PathVariable String targetUserId) {
        cartService.clearCart(targetUserId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/checkout")
    public ResponseEntity<Map<String, String>> checkout(
            @AuthenticationPrincipal String principal,
            @Valid @RequestBody CartDTO.CheckoutRequest body,
            HttpServletRequest request) {
        String userId   = resolveUserId(request, principal);
        String jwtToken = extractJwtToken(request);
        String orderId  = cartService.checkout(userId, body, jwtToken);
        return ResponseEntity.ok(Map.of(
                "message", "Đặt hàng thành công!",
                "orderId", orderId
        ));
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    /**
     * Ưu tiên lấy userId từ Gateway header (X-User-Id).
     * Fallback: principal từ GatewayHeaderAuthFilter (đã parse cùng header).
     * Nếu cả hai đều null → request không qua Gateway → trả về 401.
     */
    private String resolveUserId(HttpServletRequest request, String principal) {
        String gatewayUserId = request.getHeader(GATEWAY_USER_ID_HEADER);
        if (gatewayUserId != null && !gatewayUserId.isBlank()) return gatewayUserId;
        if (principal    != null && !principal.isBlank())    return principal;
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "Không xác định được danh tính người dùng");
    }

    private String extractJwtToken(HttpServletRequest request) {
        return (String) request.getAttribute(JWT_TOKEN_ATTRIBUTE);
    }
}
