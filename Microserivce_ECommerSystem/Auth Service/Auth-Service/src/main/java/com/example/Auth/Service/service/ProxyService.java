package com.example.Auth.Service.service;

import com.example.Auth.Service.client.OrderClient;
import com.example.Auth.Service.client.ProductClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProxyService {

    private final ProductClient productClient;
    private final OrderClient orderClient;

    // ── PRODUCT ──────────────────────────────────────────────

    public Map<String, Object> filterProducts(String keyword, int page, int size) {
        return productClient.filterProducts(keyword, page, size);
    }

    public Object getProductById(String id) {
        return productClient.getProductById(id);
    }

    public List<Object> getAllProducts() {
        return productClient.getAllProducts();
    }

    // ── ORDER ────────────────────────────────────────────────

    public Map<String, Object> getAllOrders(int page, int size) {
        return orderClient.getAllOrders(page, size);
    }

    public Object getOrderById(String id) {
        return orderClient.getOrderById(id);
    }

    public Map<String, Object> getOrdersByUser(String userId, int page, int size) {
        return orderClient.getOrdersByUser(userId, page, size);
    }
}
