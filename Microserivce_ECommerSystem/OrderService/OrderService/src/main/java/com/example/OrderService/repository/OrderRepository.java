package com.example.OrderService.repository;

import com.example.OrderService.entity.OrderEntity;
import com.example.OrderService.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Repository
public interface OrderRepository extends JpaRepository<OrderEntity, String> {

    // Lấy đơn hàng theo user (chưa xóa)

    // Lấy tất cả đơn hàng chưa xóa
    Page<OrderEntity> findByDeletedFalse(Pageable pageable);

    // Filter theo status
    Page<OrderEntity> findByStatusAndDeletedFalse(OrderStatus status, Pageable pageable);

    // Filter nâng cao
    @Query("""
        SELECT o FROM OrderEntity o
        WHERE o.deleted = false
          AND (:userId     IS NULL OR o.userId = :userId)
          AND (:status     IS NULL OR o.status = :status)
          AND (:fromDate   IS NULL OR o.createdAt >= :fromDate)
          AND (:toDate     IS NULL OR o.createdAt <= :toDate)
          AND (:minTotal   IS NULL OR o.finalAmount >= :minTotal)
          AND (:maxTotal   IS NULL OR o.finalAmount <= :maxTotal)
    """)
    Page<OrderEntity> filterOrders(
            @Param("userId")   String userId,
            @Param("status")   OrderStatus status,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate")   LocalDateTime toDate,
            @Param("minTotal") BigDecimal minTotal,
            @Param("maxTotal") BigDecimal maxTotal,
            Pageable pageable
    );
    // ✅ THÊM: Đếm theo status và chưa xóa
//    long countByDeletedFalse();
    long countByStatusAndDeletedFalse(OrderStatus status);

    // ✅ THÊM: Sản phẩm bán chạy
    @Query("""
        SELECT oi.productId         AS productId,
               oi.productName       AS productName,
               SUM(oi.quantity)     AS totalQuantity,
               SUM(oi.subtotal)   AS totalRevenue
        FROM OrderItemEntity oi
        JOIN oi.order o
        WHERE o.deleted = false
          AND o.status = 'DELIVERED'
        GROUP BY oi.productId, oi.productName
        ORDER BY totalQuantity DESC
        LIMIT :top
    """)
    List<Map<String, Object>> getBestSellingProducts(@Param("top") int top);
    // Thống kê doanh thu
    @Query("SELECT SUM(o.finalAmount) FROM OrderEntity o WHERE o.status = 'DELIVERED' AND o.deleted = false")
    BigDecimal getTotalRevenue();

    @Query("""
        SELECT SUM(o.finalAmount) FROM OrderEntity o
        WHERE o.status = 'DELIVERED'
          AND o.deleted = false
          AND o.deliveredAt BETWEEN :fromDate AND :toDate
    """)
    BigDecimal getRevenueByDateRange(@Param("fromDate") LocalDateTime fromDate,
                                     @Param("toDate")   LocalDateTime toDate);
    // Đếm tất cả đơn hàng chưa xóa
    long countByDeletedFalse();
    Page<OrderEntity> findByUserIdAndDeletedFalse(String userId, Pageable pageable);

    @Query("""
        SELECT COUNT(o) > 0
        FROM OrderEntity o
        JOIN o.items oi
        WHERE o.userId = :userId
          AND oi.productId = :productId
          AND o.status = 'DELIVERED'
          AND o.deleted = false
    """)
    boolean existsByUserIdAndProductIdAndStatus(@Param("userId") String userId, @Param("productId") String productId);
}