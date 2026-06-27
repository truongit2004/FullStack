package com.example.OrderService.service;

import com.example.OrderService.entity.OrderStatus;
import com.example.OrderService.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StatsService {
    @Autowired
    private final OrderRepository orderRepository;

    public Map<String, Object> getOverviewStats() {
        long total      = orderRepository.countByDeletedFalse();
        long pending    = orderRepository.countByStatusAndDeletedFalse(OrderStatus.PENDING);
        long confirmed  = orderRepository.countByStatusAndDeletedFalse(OrderStatus.CONFIRMED);
        long shipping   = orderRepository.countByStatusAndDeletedFalse(OrderStatus.SHIPPING);
        long delivered  = orderRepository.countByStatusAndDeletedFalse(OrderStatus.DELIVERED);
        long cancelled  = orderRepository.countByStatusAndDeletedFalse(OrderStatus.CANCELLED);
        BigDecimal revenue = orderRepository.getTotalRevenue();

        return Map.of(
                "totalOrders", total,
                "pending",     pending,
                "confirmed",   confirmed,
                "shipping",    shipping,
                "delivered",   delivered,
                "cancelled",   cancelled,
                "totalRevenue", revenue != null ? revenue : BigDecimal.ZERO
        );
    }

    public Map<String, Object> getRevenue(LocalDateTime fromDate, LocalDateTime toDate) {
        // Nếu không truyền thì lấy tháng hiện tại
        LocalDateTime from = fromDate != null ? fromDate : LocalDateTime.now().withDayOfMonth(1).withHour(0);
        LocalDateTime to   = toDate   != null ? toDate   : LocalDateTime.now();

        BigDecimal revenue = orderRepository.getRevenueByDateRange(from, to);
        return Map.of(
                "fromDate", from,
                "toDate",   to,
                "revenue",  revenue != null ? revenue : BigDecimal.ZERO
        );
    }

    public List<Map<String, Object>> getBestSellingProducts(int top) {
        return orderRepository.getBestSellingProducts(top);
    }
}