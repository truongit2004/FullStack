package com.example.OrderService.entity;

public enum OrderStatus {
    PENDING,    // Chờ xác nhận
    CONFIRMED,  // Đã xác nhận
    SHIPPING,   // Đang giao hàng
    DELIVERED,  // Đã giao hàng
    CANCELLED,  // Đã hủy
    REFUNDED,   // Đã hoàn tiền
    PAID,       // Đã thanh toán
    RETURNED    // Đã trả hàng
}