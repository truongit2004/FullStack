package com.example.OrderService.controller;

import com.example.OrderService.dto.OrderRequestDTO;
import com.example.OrderService.dto.OrderResponseDTO;
import com.example.OrderService.entity.OrderStatus;
import com.example.OrderService.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    // ─────────────────────────────────────────────
    // TẠO & LẤY TẤT CẢ (ADMIN ONLY)
    // ─────────────────────────────────────────────
    @PostMapping
    public ResponseEntity<OrderResponseDTO> createOrder(
            @Valid @RequestBody OrderRequestDTO request,
            @RequestHeader(name = "X-User-Id", required = false) String authenticatedUserId) {
        
        if (authenticatedUserId != null && request.getUserId() != null) {
            if (!authenticatedUserId.equals(request.getUserId())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
            }
        }
        
        if (authenticatedUserId != null) {
            request.setUserId(authenticatedUserId);
        }
        
        return ResponseEntity.ok(orderService.createOrder(request));
    }

    @GetMapping
    public ResponseEntity<Page<OrderResponseDTO>> getAllOrders(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestHeader(name = "X-User-Role", required = false) String authRole) {

        if (authRole == null || !"ADMIN".equalsIgnoreCase(authRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(orderService.getAllOrders(pageable));
    }

    // ─────────────────────────────────────────────
    // CHI TIẾT & CẬP NHẬT
    // ─────────────────────────────────────────────
    @GetMapping("/{id}")
    public ResponseEntity<OrderResponseDTO> getOrderById(@PathVariable String id) {
        return ResponseEntity.ok(orderService.getOrderById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<OrderResponseDTO> updateOrder(@PathVariable String id,
                                                        @Valid @RequestBody OrderRequestDTO request) {
        return ResponseEntity.ok(orderService.updateOrder(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOrder(@PathVariable String id) {
        orderService.deleteOrder(id);
        return ResponseEntity.noContent().build();
    }

    // ─────────────────────────────────────────────
    // QUẢN LÝ TRẠNG THÁI (DÀNH CHO ADMIN)
    // ─────────────────────────────────────────────
    @PutMapping("/{id}/confirm")
    public ResponseEntity<OrderResponseDTO> confirmOrder(@PathVariable String id) {
        return ResponseEntity.ok(orderService.confirmOrder(id));
    }

    @PutMapping("/{id}/ship")
    public ResponseEntity<OrderResponseDTO> shipOrder(@PathVariable String id) {
        return ResponseEntity.ok(orderService.shipOrder(id));
    }

    @PutMapping("/{id}/deliver")
    public ResponseEntity<OrderResponseDTO> deliverOrder(@PathVariable String id) {
        return ResponseEntity.ok(orderService.deliverOrder(id));
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<OrderResponseDTO> cancelOrder(
            @PathVariable String id,
            @RequestHeader(name = "X-User-Id", required = false) String authId,
            @RequestHeader(name = "X-User-Role", required = false) String authRole) {
        return ResponseEntity.ok(orderService.cancelOrder(id, authId, authRole));
    }

    @PutMapping("/{id}/refund")
    public ResponseEntity<OrderResponseDTO> refundOrder(@PathVariable String id) {
        return ResponseEntity.ok(orderService.refundOrder(id));
    }

    @PutMapping("/{id}/return")
    public ResponseEntity<OrderResponseDTO> returnOrder(@PathVariable String id) {
        return ResponseEntity.ok(orderService.returnOrder(id));
    }

    @PostMapping("/{id}/paid")
    public ResponseEntity<Void> markOrderAsPaid(@PathVariable String id) {
        orderService.markOrderAsPaid(id);
        return ResponseEntity.ok().build();
    }

    // ─────────────────────────────────────────────
    // BỘ LỌC NÂNG CAO (DÀNH CHO ADMIN)
    // ─────────────────────────────────────────────
    @GetMapping("/filter")
    public ResponseEntity<Page<OrderResponseDTO>> filterOrders(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,
            @RequestParam(required = false) BigDecimal minTotal,
            @RequestParam(required = false) BigDecimal maxTotal,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestHeader(name = "X-User-Role", required = false) String authRole) {

        // CHỈ ADMIN MỚI ĐƯỢC PHÉP DÙNG BỘ LỌC TOÀN HỆ THỐNG
        if (authRole == null || !"ADMIN".equalsIgnoreCase(authRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(
                orderService.filterOrders(userId, status, fromDate, toDate, minTotal, maxTotal, pageable)
        );
    }

    // ─────────────────────────────────────────────
    // LẤY ĐƠN THEO USER (CÁ NHÂN HOẶC ADMIN)
    // ─────────────────────────────────────────────
    @GetMapping("/user/{userId}")
    public ResponseEntity<Page<OrderResponseDTO>> getOrdersByUser(
            @PathVariable String userId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestHeader(name = "X-User-Id", required = false) String authId,
            @RequestHeader(name = "X-User-Role", required = false) String authRole) {

        if (authRole != null && !"ADMIN".equalsIgnoreCase(authRole)) {
            if (authId != null && !authId.equals(userId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(orderService.getOrdersByUser(userId, pageable));
    }

    @GetMapping("/my-orders/{userId}")
    public ResponseEntity<Page<OrderResponseDTO>> getMyOrders(
            @PathVariable String userId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestHeader(name = "X-User-Id", required = false) String authId,
            @RequestHeader(name = "X-User-Role", required = false) String authRole) {
        
        return getOrdersByUser(userId, page, size, authId, authRole);
    }

    @GetMapping("/check-purchased")
    public ResponseEntity<Boolean> checkPurchased(@RequestParam String userId, @RequestParam String productId) {
        return ResponseEntity.ok(orderService.hasPurchasedProduct(userId, productId));
    }
}