package com.example.OrderService.service;

import com.example.OrderService.dto.OrderRequestDTO;
import com.example.OrderService.dto.OrderResponseDTO;
import com.example.OrderService.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface OrderService {

    // CRUD
    OrderResponseDTO createOrder(OrderRequestDTO request);
    Page<OrderResponseDTO> getAllOrders(Pageable pageable);
    OrderResponseDTO getOrderById(String id);
    OrderResponseDTO updateOrder(String id, OrderRequestDTO request);
    void deleteOrder(String id);

    // Trạng thái
    OrderResponseDTO confirmOrder(String id);
    OrderResponseDTO shipOrder(String id);
    OrderResponseDTO deliverOrder(String id);
    OrderResponseDTO cancelOrder(String id, String authId, String authRole);
    OrderResponseDTO refundOrder(String id);
    OrderResponseDTO returnOrder(String id);
    void markOrderAsPaid(String id);

    // User & Filter
    Page<OrderResponseDTO> getOrdersByUser(String userId, Pageable pageable);
    Page<OrderResponseDTO> filterOrders(String userId, OrderStatus status,
                                        LocalDateTime fromDate, LocalDateTime toDate,
                                        BigDecimal minTotal, BigDecimal maxTotal,
                                        Pageable pageable);
    boolean hasPurchasedProduct(String userId, String productId);
}