package com.example.OrderService;

import com.example.OrderService.client.InventoryClient;
import com.example.OrderService.client.PointClient;
import com.example.OrderService.client.ProductClient;
import com.example.OrderService.client.ShippingClient;
import com.example.OrderService.client.UserClient;
import com.example.OrderService.dto.InventoryResponseDTO;
import com.example.OrderService.dto.OrderItemDTO;
import com.example.OrderService.dto.OrderRequestDTO;
import com.example.OrderService.dto.ProductResponseDTO;
import com.example.OrderService.dto.UserResponseDTO;
import com.example.OrderService.entity.OrderEntity;
import com.example.OrderService.entity.OrderStatus;
import com.example.OrderService.exception.AppException;
import com.example.OrderService.exception.ErrorCode;
import com.example.OrderService.repository.OrderRepository;
import com.example.OrderService.retry.PendingTaskEntity;
import com.example.OrderService.retry.PendingTaskProcessor;
import com.example.OrderService.retry.PendingTaskRepository;
import com.example.OrderService.retry.PendingTaskType;
import com.example.OrderService.service.OrderService;
import feign.FeignException;
import feign.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Kiểm tra: khi service kia (Point, Shipping, User, Kafka) chết, thao tác chính của đơn hàng vẫn xong
 * và việc còn nợ được ghi lại rồi tự làm lại khi service hoạt động trở lại.
 *
 * Cần MySQL đang chạy ở localhost; dùng database riêng "order_db_resilience_test" (không đụng order_db).
 * Các service khác và Kafka được giả lập.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:mysql://localhost:3306/order_db_resilience_test?createDatabaseIfNotExist=true",
        "spring.jpa.show-sql=false",
        "eureka.client.enabled=false",
        // Tắt lịch chạy nền: test tự gọi processor.run() để kết quả xác định
        "order.pending-task.initial-delay-ms=3600000",
        "order.pending-task.interval-ms=3600000"
})
class OrderResilienceTest {

    @Autowired OrderService orderService;
    @Autowired OrderRepository orderRepository;
    @Autowired PendingTaskRepository taskRepository;
    @Autowired PendingTaskProcessor processor;

    @MockitoBean PointClient pointClient;
    @MockitoBean ShippingClient shippingClient;
    @MockitoBean UserClient userClient;
    @MockitoBean ProductClient productClient;
    @MockitoBean InventoryClient inventoryClient;
    @MockitoBean KafkaTemplate<String, String> kafkaTemplate;

    @BeforeEach
    void clean() {
        taskRepository.deleteAll();
        orderRepository.deleteAll();
    }

    private OrderEntity newOrder(OrderStatus status) {
        return orderRepository.save(OrderEntity.builder()
                .userId("42")
                .status(status)
                .items(List.of())
                .totalAmount(new BigDecimal("100"))
                .shippingFee(new BigDecimal("8"))
                .discountAmount(BigDecimal.ZERO)
                .finalAmount(new BigDecimal("108"))
                .shippingAddress("1 Le Loi, TP.HCM")
                .deleted(false)
                .build());
    }

    private PendingTaskEntity task(PendingTaskType type, String orderId) {
        return taskRepository.findByTypeAndOrderId(type, orderId).orElse(null);
    }

    /** Coi như đã tới giờ thử lại. */
    private void makeDue() {
        taskRepository.findAll().forEach(t -> {
            t.setNextAttemptAt(LocalDateTime.now().minusSeconds(1));
            taskRepository.save(t);
        });
    }

    private FeignException.NotFound notFound() {
        Request request = Request.create(Request.HttpMethod.GET, "http://x", Map.of(), null, StandardCharsets.UTF_8, null);
        return new FeignException.NotFound("not found", request, null, Map.of());
    }

    // ── Point Service chết lúc hủy đơn ───────────────────────────────────────

    @Test
    void cancel_whenPointServiceDown_orderStillCancelled_andPointsReversedLater() {
        OrderEntity order = newOrder(OrderStatus.PENDING);
        doThrow(new RuntimeException("point-service down")).when(pointClient).reverse(order.getId());

        orderService.cancelOrder(order.getId(), "1", "ADMIN");

        assertEquals(OrderStatus.CANCELLED, orderRepository.findById(order.getId()).orElseThrow().getStatus(),
                "Hủy đơn không được thất bại vì Point Service chết");
        PendingTaskEntity pending = task(PendingTaskType.REVERSE_POINTS, order.getId());
        assertNotNull(pending, "Việc hoàn điểm phải được ghi lại");
        assertEquals(PendingTaskEntity.Status.PENDING, pending.getStatus());

        // Point Service vẫn chết: thử lại thất bại, tăng số lần, vẫn chờ
        makeDue();
        processor.run();
        pending = task(PendingTaskType.REVERSE_POINTS, order.getId());
        assertEquals(PendingTaskEntity.Status.PENDING, pending.getStatus());
        assertEquals(1, pending.getAttempts());
        assertTrue(pending.getNextAttemptAt().isAfter(LocalDateTime.now()), "Phải lùi thời điểm thử lại");

        // Point Service sống lại
        doNothing().when(pointClient).reverse(order.getId());
        makeDue();
        processor.run();
        assertEquals(PendingTaskEntity.Status.DONE, task(PendingTaskType.REVERSE_POINTS, order.getId()).getStatus());
        verify(pointClient, times(3)).reverse(order.getId());

        // Việc đã xong thì không chạy lại nữa
        makeDue();
        processor.run();
        verify(pointClient, times(3)).reverse(order.getId());
    }

    @Test
    void cancel_whenPointServiceUp_noPendingTask() {
        OrderEntity order = newOrder(OrderStatus.PENDING);

        orderService.cancelOrder(order.getId(), "1", "ADMIN");

        verify(pointClient).reverse(order.getId());
        assertNull(task(PendingTaskType.REVERSE_POINTS, order.getId()));
    }

    // ── Shipping Service chết lúc giao hàng ──────────────────────────────────

    @Test
    void ship_whenShippingServiceDown_orderStillShipping_andTicketCreatedLater() {
        OrderEntity order = newOrder(OrderStatus.CONFIRMED);
        when(shippingClient.getByOrderId(order.getId())).thenThrow(new RuntimeException("shipping-service down"));

        orderService.shipOrder(order.getId());

        assertEquals(OrderStatus.SHIPPING, orderRepository.findById(order.getId()).orElseThrow().getStatus());
        assertNotNull(task(PendingTaskType.CREATE_SHIPPING, order.getId()), "Việc tạo vận đơn phải được ghi lại");
        verify(shippingClient, never()).createShipping(any());

        // Shipping Service sống lại: chưa có vận đơn (404) -> tạo mới
        reset(shippingClient);
        when(shippingClient.getByOrderId(order.getId())).thenThrow(notFound());
        when(userClient.getUserById("42")).thenReturn(UserResponseDTO.builder().id("42").username("alice").build());
        makeDue();
        processor.run();

        assertEquals(PendingTaskEntity.Status.DONE, task(PendingTaskType.CREATE_SHIPPING, order.getId()).getStatus());
        verify(shippingClient).createShipping(argThat(req ->
                req.getOrderId().equals(order.getId()) && "alice".equals(req.getRecipientName())));
    }

    @Test
    void ship_retryIsSkipped_whenOrderNoLongerShipping() {
        OrderEntity order = newOrder(OrderStatus.CONFIRMED);
        when(shippingClient.getByOrderId(order.getId())).thenThrow(new RuntimeException("down"));
        orderService.shipOrder(order.getId());

        // Đơn bị hủy trước khi kịp tạo vận đơn
        OrderEntity cancelled = orderRepository.findById(order.getId()).orElseThrow();
        cancelled.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(cancelled);

        reset(shippingClient);
        makeDue();
        processor.run();

        assertEquals(PendingTaskEntity.Status.DONE, task(PendingTaskType.CREATE_SHIPPING, order.getId()).getStatus());
        verifyNoInteractions(shippingClient);
    }

    // ── Kafka chết lúc giao hàng thành công ──────────────────────────────────

    @Test
    void deliver_whenKafkaDown_orderStillDelivered_andPointEventPublishedLater() {
        OrderEntity order = newOrder(OrderStatus.SHIPPING);
        when(kafkaTemplate.send(anyString(), anyString())).thenThrow(new RuntimeException("kafka down"));

        orderService.deliverOrder(order.getId());

        assertEquals(OrderStatus.DELIVERED, orderRepository.findById(order.getId()).orElseThrow().getStatus());
        assertNotNull(task(PendingTaskType.PUBLISH_POINT_EARN, order.getId()), "Sự kiện cộng điểm phải được ghi lại");

        // Kafka sống lại
        reset(kafkaTemplate);
        when(kafkaTemplate.send(anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
        makeDue();
        processor.run();

        assertEquals(PendingTaskEntity.Status.DONE, task(PendingTaskType.PUBLISH_POINT_EARN, order.getId()).getStatus());
        verify(kafkaTemplate).send(eq("order-delivered-topic"), contains(order.getId()));
    }

    // ── Tạo đơn ──────────────────────────────────────────────────────────────

    private OrderRequestDTO orderRequest(BigDecimal pointsToRedeem) {
        when(userClient.getUserById("42")).thenReturn(UserResponseDTO.builder().id("42").username("alice").build());
        when(productClient.getProductById("p1")).thenReturn(ProductResponseDTO.builder()
                .id("p1").name("Ao thun").price(new BigDecimal("100")).productStatus("ACTIVE").build());
        InventoryResponseDTO stock = new InventoryResponseDTO();
        stock.setProductId("p1");
        stock.setStockQuantity(10);
        when(inventoryClient.getStock("p1")).thenReturn(stock);

        return OrderRequestDTO.builder()
                .userId("42")
                .items(List.of(OrderItemDTO.builder().productId("p1").quantity(1).build()))
                .shippingAddress("1 Le Loi, TP.HCM")
                .pointsToRedeem(pointsToRedeem)
                .build();
    }

    @Test
    void createOrder_whenPointServiceDown_failsCleanly_andSchedulesReverseOutsideRolledBackTransaction() {
        OrderRequestDTO request = orderRequest(new BigDecimal("20"));
        when(pointClient.redeem(any())).thenThrow(new RuntimeException("read timed out"));

        AppException ex = assertThrows(AppException.class, () -> orderService.createOrder(request));

        assertEquals(ErrorCode.POINT_SERVICE_UNAVAILABLE, ex.getErrorCode());
        assertEquals(0, orderRepository.count(), "Đơn phải được rollback");
        List<PendingTaskEntity> tasks = taskRepository.findAll();
        assertEquals(1, tasks.size(), "Việc hoàn điểm phải còn lại dù transaction tạo đơn đã rollback");
        assertEquals(PendingTaskType.REVERSE_POINTS, tasks.get(0).getType());
        verify(inventoryClient, never()).decreaseStockBulk(any());
    }

    @Test
    void createOrder_whenStockDecreaseFails_andPointServiceAlsoDown_reverseIsScheduled() {
        OrderRequestDTO request = orderRequest(new BigDecimal("20"));
        when(pointClient.redeem(any())).thenReturn(com.example.OrderService.dto.PointRedeemResponseDTO.builder()
                .discountAmount(new BigDecimal("20")).build());
        doThrow(new RuntimeException("inventory down")).when(inventoryClient).decreaseStockBulk(any());
        doThrow(new RuntimeException("point down")).when(pointClient).reverse(anyString());

        assertThrows(AppException.class, () -> orderService.createOrder(request));

        assertEquals(0, orderRepository.count());
        assertEquals(1, taskRepository.findAll().size(), "Điểm đã trừ phải được hoàn lại sau");
    }

    @Test
    void createOrder_whenUserServiceDown_reportsServiceUnavailable_notUserNotFound() {
        OrderRequestDTO request = orderRequest(null);
        when(userClient.getUserById("42")).thenThrow(new RuntimeException("connection refused"));

        AppException ex = assertThrows(AppException.class, () -> orderService.createOrder(request));

        assertEquals(ErrorCode.USER_SERVICE_UNAVAILABLE, ex.getErrorCode());
    }

    @Test
    void createOrder_whenUserReallyMissing_reportsUserNotFound() {
        OrderRequestDTO request = orderRequest(null);
        when(userClient.getUserById("42")).thenThrow(notFound());

        AppException ex = assertThrows(AppException.class, () -> orderService.createOrder(request));

        assertEquals(ErrorCode.USER_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void createOrder_happyPath_stillWorks() {
        OrderRequestDTO request = orderRequest(new BigDecimal("20"));
        when(pointClient.redeem(any())).thenReturn(com.example.OrderService.dto.PointRedeemResponseDTO.builder()
                .discountAmount(new BigDecimal("20")).build());
        when(kafkaTemplate.send(anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));

        var response = orderService.createOrder(request);

        assertEquals(0, new BigDecimal("20").compareTo(response.getDiscountAmount()));
        assertEquals(1, orderRepository.count());
        assertEquals(0, taskRepository.count());
        verify(inventoryClient).decreaseStockBulk(any());
    }

    // ── Payment Service báo "đã thanh toán" trễ hoặc lặp lại ─────────────────

    @Test
    void markPaid_isSafeToRepeat_andNeverOverwritesLaterStatus() {
        OrderEntity pending = newOrder(OrderStatus.PENDING);
        orderService.markOrderAsPaid(pending.getId());
        orderService.markOrderAsPaid(pending.getId());
        assertEquals(OrderStatus.PAID, orderRepository.findById(pending.getId()).orElseThrow().getStatus());

        // Báo trễ khi đơn đã đi tiếp: không được kéo trạng thái lùi về PAID
        OrderEntity shipping = newOrder(OrderStatus.SHIPPING);
        orderService.markOrderAsPaid(shipping.getId());
        assertEquals(OrderStatus.SHIPPING, orderRepository.findById(shipping.getId()).orElseThrow().getStatus());

        // Đơn đã hủy: không được "hồi sinh" thành PAID
        OrderEntity cancelled = newOrder(OrderStatus.CANCELLED);
        orderService.markOrderAsPaid(cancelled.getId());
        assertEquals(OrderStatus.CANCELLED, orderRepository.findById(cancelled.getId()).orElseThrow().getStatus());
    }

    // ── Bỏ cuộc sau quá nhiều lần ────────────────────────────────────────────

    @Test
    void task_isMarkedFailed_afterMaxAttempts() {
        OrderEntity order = newOrder(OrderStatus.PENDING);
        doThrow(new RuntimeException("down")).when(pointClient).reverse(order.getId());
        orderService.cancelOrder(order.getId(), "1", "ADMIN");

        PendingTaskEntity pending = task(PendingTaskType.REVERSE_POINTS, order.getId());
        pending.setAttempts(39);
        taskRepository.save(pending);
        makeDue();
        processor.run();

        pending = task(PendingTaskType.REVERSE_POINTS, order.getId());
        assertEquals(PendingTaskEntity.Status.FAILED, pending.getStatus());

        // Đã bỏ cuộc thì không tự thử nữa
        makeDue();
        processor.run();
        verify(pointClient, times(2)).reverse(order.getId());
    }
}
