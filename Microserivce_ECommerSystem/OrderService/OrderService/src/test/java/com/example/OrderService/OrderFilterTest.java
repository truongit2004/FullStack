package com.example.OrderService;

import com.example.OrderService.client.CouponClient;
import com.example.OrderService.client.InventoryClient;
import com.example.OrderService.client.PointClient;
import com.example.OrderService.client.ProductClient;
import com.example.OrderService.client.ShippingClient;
import com.example.OrderService.client.UserClient;
import com.example.OrderService.dto.OrderResponseDTO;
import com.example.OrderService.entity.OrderEntity;
import com.example.OrderService.entity.OrderStatus;
import com.example.OrderService.repository.OrderRepository;
import com.example.OrderService.retry.PendingTaskRepository;
import com.example.OrderService.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kiểm tra màn hình Admin xem đơn của TOÀN HỆ THỐNG: lọc theo ngày đặt, theo trạng thái, phân trang.
 * Tham số ngày được truyền đúng như FE gửi: từ 00:00:00 của ngày đầu tới 23:59:59 của ngày cuối.
 *
 * Cần MySQL đang chạy ở localhost; dùng database riêng "order_db_resilience_test".
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:mysql://localhost:3306/order_db_resilience_test?createDatabaseIfNotExist=true",
        "spring.jpa.show-sql=false",
        "eureka.client.enabled=false",
        "order.pending-task.initial-delay-ms=3600000",
        "order.pending-task.interval-ms=3600000"
})
class OrderFilterTest {

    @Autowired OrderService orderService;
    @Autowired OrderRepository orderRepository;
    @Autowired PendingTaskRepository taskRepository;
    @Autowired JdbcTemplate jdbc;

    @MockitoBean CouponClient couponClient;
    @MockitoBean PointClient pointClient;
    @MockitoBean ShippingClient shippingClient;
    @MockitoBean UserClient userClient;
    @MockitoBean ProductClient productClient;
    @MockitoBean InventoryClient inventoryClient;
    @MockitoBean KafkaTemplate<String, String> kafkaTemplate;

    // Giống OrderController: mới nhất trước
    private Pageable page(int page, int size) {
        return PageRequest.of(page, size, Sort.by("createdAt").descending());
    }

    /** Tạo đơn rồi đặt lại ngày tạo (cột created_at tự gán lúc lưu nên phải sửa bằng SQL). */
    private String order(String userId, OrderStatus status, String createdAt, boolean deleted) {
        OrderEntity saved = orderRepository.save(OrderEntity.builder()
                .userId(userId).status(status).items(List.of())
                .totalAmount(new BigDecimal("100")).shippingFee(BigDecimal.ZERO).discountAmount(BigDecimal.ZERO)
                .finalAmount(new BigDecimal("100")).deleted(deleted).build());
        jdbc.update("UPDATE orders SET created_at = ? WHERE id = ?", createdAt, saved.getId());
        return saved.getId();
    }

    String oct10Morning, oct10Night, oct11, oct12Start, oct12End, oct13, deletedOct11;

    @BeforeEach
    void seed() {
        taskRepository.deleteAll();
        orderRepository.deleteAll();
        oct10Morning = order("u1", OrderStatus.DELIVERED, "2026-10-10 08:00:00", false);
        oct10Night   = order("u2", OrderStatus.PENDING,   "2026-10-10 23:30:00", false);
        oct11        = order("u1", OrderStatus.PENDING,   "2026-10-11 12:00:00", false);
        oct12Start   = order("u3", OrderStatus.CANCELLED, "2026-10-12 00:00:00", false);
        oct12End     = order("u2", OrderStatus.PENDING,   "2026-10-12 23:59:59", false);
        oct13        = order("u3", OrderStatus.PENDING,   "2026-10-13 00:00:01", false);
        deletedOct11 = order("u1", OrderStatus.PENDING,   "2026-10-11 09:00:00", true);
    }

    private List<String> ids(Page<OrderResponseDTO> page) {
        return page.getContent().stream().map(OrderResponseDTO::getId).toList();
    }

    private LocalDateTime startOf(String day) { return LocalDateTime.parse(day + "T00:00:00"); }
    private LocalDateTime endOf(String day) { return LocalDateTime.parse(day + "T23:59:59"); }

    @Test
    void noFilter_showsAllOrdersOfAllUsers_newestFirst_withoutDeletedOnes() {
        Page<OrderResponseDTO> result = orderService.filterOrders(null, null, null, null, null, null, page(0, 20));

        assertEquals(List.of(oct13, oct12End, oct12Start, oct11, oct10Night, oct10Morning), ids(result));
        assertEquals(6, result.getTotalElements());
    }

    @Test
    void singleDay_includesTheWholeDay() {
        Page<OrderResponseDTO> result = orderService.filterOrders(null, null,
                startOf("2026-10-12"), endOf("2026-10-12"), null, null, page(0, 20));

        assertEquals(List.of(oct12End, oct12Start), ids(result),
                "Đơn lúc 00:00:00 và 23:59:59 đều thuộc ngày 12; đơn 00:00:01 ngày 13 thì không");
    }

    @Test
    void dateRange_acrossSeveralDays_andAllUsers() {
        Page<OrderResponseDTO> result = orderService.filterOrders(null, null,
                startOf("2026-10-10"), endOf("2026-10-11"), null, null, page(0, 20));

        assertEquals(List.of(oct11, oct10Night, oct10Morning), ids(result));
    }

    @Test
    void onlyFromDate_orOnlyToDate() {
        assertEquals(List.of(oct13, oct12End, oct12Start),
                ids(orderService.filterOrders(null, null, startOf("2026-10-12"), null, null, null, page(0, 20))));
        assertEquals(List.of(oct10Night, oct10Morning),
                ids(orderService.filterOrders(null, null, null, endOf("2026-10-10"), null, null, page(0, 20))));
    }

    @Test
    void dateCombinedWithStatus() {
        Page<OrderResponseDTO> result = orderService.filterOrders(null, OrderStatus.PENDING,
                startOf("2026-10-10"), endOf("2026-10-12"), null, null, page(0, 20));

        assertEquals(List.of(oct12End, oct11, oct10Night), ids(result));
    }

    @Test
    void pagination_walksThroughOlderOrders() {
        Page<OrderResponseDTO> first = orderService.filterOrders(null, null, null, null, null, null, page(0, 4));
        Page<OrderResponseDTO> second = orderService.filterOrders(null, null, null, null, null, null, page(1, 4));

        assertEquals(2, first.getTotalPages());
        assertEquals(6, first.getTotalElements());
        assertEquals(List.of(oct13, oct12End, oct12Start, oct11), ids(first));
        assertEquals(List.of(oct10Night, oct10Morning), ids(second), "Trang 2 là các đơn cũ hơn");
    }

    @Test
    void dayWithNoOrders_returnsEmptyPage() {
        Page<OrderResponseDTO> result = orderService.filterOrders(null, null,
                startOf("2026-09-01"), endOf("2026-09-01"), null, null, page(0, 20));

        assertTrue(result.isEmpty());
        assertEquals(0, result.getTotalElements());
    }
}
