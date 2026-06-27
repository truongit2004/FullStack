package com.example.Auth.Service.controller;

import com.example.Auth.Service.service.ProxyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ProxyController {

    private final ProxyService proxyService;

    // ════════════════════════════════════════════════════════
    // PRODUCT ENDPOINTS (proxy → Product-Service :8082)
    // ════════════════════════════════════════════════════════

    /** Lọc sản phẩm có phân trang */
    @GetMapping("/proxy/products")
    public ResponseEntity<Map<String, Object>> filterProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(proxyService.filterProducts(keyword, page, size));
    }

    /** Lấy chi tiết một sản phẩm */
    @GetMapping("/proxy/products/{id}")
    public ResponseEntity<Object> getProductById(@PathVariable String id) {
        return ResponseEntity.ok(proxyService.getProductById(id));
    }

    /** Lấy tất cả sản phẩm (kể cả đã xóa mềm) - chỉ ADMIN */
    @GetMapping("/proxy/products/all")
    public ResponseEntity<List<Object>> getAllProducts() {
        return ResponseEntity.ok(proxyService.getAllProducts());
    }

    // ════════════════════════════════════════════════════════
    // ORDER ENDPOINTS (proxy → Order-Service :8084)
    // ════════════════════════════════════════════════════════

    /** Lấy tất cả đơn hàng (có phân trang) - chỉ ADMIN */
    @GetMapping("/proxy/orders")
    public ResponseEntity<Map<String, Object>> getAllOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(proxyService.getAllOrders(page, size));
    }

    /** Lấy chi tiết một đơn hàng */
    @GetMapping("/proxy/orders/{id}")
    public ResponseEntity<Object> getOrderById(@PathVariable String id) {
        return ResponseEntity.ok(proxyService.getOrderById(id));
    }

    /** Lấy đơn hàng của một user */
    @GetMapping("/proxy/orders/user/{userId}")
    public ResponseEntity<Map<String, Object>> getOrdersByUser(
            @PathVariable String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(proxyService.getOrdersByUser(userId, page, size));
    }
}
