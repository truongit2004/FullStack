package com.example.OrderService;

import com.example.OrderService.client.CouponClient;
import com.example.OrderService.client.InventoryClient;
import com.example.OrderService.client.PointClient;
import com.example.OrderService.client.ProductClient;
import com.example.OrderService.client.ShippingClient;
import com.example.OrderService.client.UserClient;
import com.example.OrderService.dto.CouponDiscountResponseDTO;
import com.example.OrderService.dto.InventoryResponseDTO;
import com.example.OrderService.dto.OrderItemDTO;
import com.example.OrderService.dto.OrderRequestDTO;
import com.example.OrderService.dto.OrderResponseDTO;
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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Kiểm tra 3 quy tắc nghiệp vụ của mã giảm giá ở phía đơn hàng:
 *  1. Mã KHÔNG dùng chung với điểm thưởng.
 *  2. Hủy đơn / trả hàng thì hoàn lại lượt dùng mã.
 *  3. Đơn có dùng mã thì KHÔNG được tích điểm khi giao thành công.
 *
 * Cần MySQL đang chạy ở localhost; dùng database riêng "order_db_resilience_test".
 * Các service khác và Kafka được giả lập.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:mysql://localhost:3306/order_db_resilience_test?createDatabaseIfNotExist=true",
        "spring.jpa.show-sql=false",
        "eureka.client.enabled=false",
        "order.pending-task.initial-delay-ms=3600000",
        "order.pending-task.interval-ms=3600000"
})
class OrderCouponTest {

    @Autowired OrderService orderService;
    @Autowired OrderRepository orderRepository;
    @Autowired PendingTaskRepository taskRepository;
    @Autowired PendingTaskProcessor processor;

    @MockitoBean CouponClient couponClient;
    @MockitoBean PointClient pointClient;
    @MockitoBean ShippingClient shippingClient;
    @MockitoBean UserClient userClient;
    @MockitoBean ProductClient productClient;
    @MockitoBean InventoryClient inventoryClient;
    @MockitoBean KafkaTemplate<String, String> kafkaTemplate;

    @BeforeEach
    void setUp() {
        taskRepository.deleteAll();
        orderRepository.deleteAll();

        when(userClient.getUserById("42")).thenReturn(UserResponseDTO.builder().id("42").username("alice").build());
        when(productClient.getProductById("p1")).thenReturn(ProductResponseDTO.builder()
                .id("p1").name("Ao thun").price(new BigDecimal("100")).productStatus("ACTIVE").build());
        InventoryResponseDTO stock = new InventoryResponseDTO();
        stock.setProductId("p1");
        stock.setStockQuantity(10);
        when(inventoryClient.getStock("p1")).thenReturn(stock);
        when(kafkaTemplate.send(anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
    }

    private OrderRequestDTO request(String couponCode, String points) {
        return OrderRequestDTO.builder()
                .userId("42")
                .items(List.of(OrderItemDTO.builder().productId("p1").quantity(1).build()))
                .shippingAddress("1 Le Loi, TP.HCM")
                .couponCode(couponCode)
                .pointsToRedeem(points == null ? null : new BigDecimal(points))
                .build();
    }

    private void couponGives(String discount) {
        when(couponClient.redeem(any())).thenReturn(CouponDiscountResponseDTO.builder()
                .code("SALE10").discountAmount(new BigDecimal(discount)).build());
    }

    private OrderEntity orderWithCoupon(OrderStatus status, String couponCode) {
        return orderRepository.save(OrderEntity.builder()
                .userId("42").status(status).items(List.of())
                .totalAmount(new BigDecimal("100")).shippingFee(BigDecimal.ZERO)
                .discountAmount(couponCode == null ? BigDecimal.ZERO : BigDecimal.TEN)
                .finalAmount(new BigDecimal("90")).couponCode(couponCode)
                .shippingAddress("1 Le Loi").deleted(false).build());
    }

    private FeignException.BadRequest rejected(String message) {
        Request request = Request.create(Request.HttpMethod.POST, "http://coupon/redeem", Map.of(), null, StandardCharsets.UTF_8, null);
        byte[] body = ("{\"code\":400,\"error\":\"COUPON_EXPIRED\",\"message\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
        return new FeignException.BadRequest("bad request", request, body, Map.of());
    }

    // ── Đặt hàng có mã ───────────────────────────────────────────────────────

    @Test
    void couponIsApplied_andStoredOnOrder() {
        couponGives("10");

        OrderResponseDTO order = orderService.createOrder(request(" sale10 ", null));

        assertEquals("SALE10", order.getCouponCode(), "Mã được chuẩn hóa thành chữ hoa");
        assertEquals(0, new BigDecimal("10").compareTo(order.getDiscountAmount()));
        assertEquals(0, order.getTotalAmount().add(order.getShippingFee()).subtract(new BigDecimal("10"))
                .compareTo(order.getFinalAmount()));
        verify(couponClient).redeem(argThat(r -> r.getCode().equals("SALE10") && r.getUserId().equals("42")
                && r.getOrderId().equals(order.getId()) && r.getOrderAmount().compareTo(new BigDecimal("100")) == 0));
        verify(pointClient, never()).redeem(any());
        assertEquals("SALE10", orderRepository.findById(order.getId()).orElseThrow().getCouponCode());
    }

    @Test
    void rule1_couponCannotBeCombinedWithPoints() {
        AppException ex = assertThrows(AppException.class, () -> orderService.createOrder(request("SALE10", "20")));

        assertEquals(ErrorCode.COUPON_AND_POINTS_NOT_COMBINABLE, ex.getErrorCode());
        assertEquals(0, orderRepository.count());
        verifyNoInteractions(couponClient, pointClient);
        verify(inventoryClient, never()).decreaseStockBulk(any());
    }

    @Test
    void pointsOnly_stillWorks_andBlankCouponIsIgnored() {
        when(pointClient.redeem(any())).thenReturn(com.example.OrderService.dto.PointRedeemResponseDTO.builder()
                .discountAmount(new BigDecimal("20")).build());

        OrderResponseDTO order = orderService.createOrder(request("   ", "20"));

        assertNull(order.getCouponCode());
        assertEquals(0, new BigDecimal("20").compareTo(order.getDiscountAmount()));
        verifyNoInteractions(couponClient);
    }

    @Test
    void rejectedCoupon_showsTheRealReason_andCreatesNoOrder() {
        when(couponClient.redeem(any())).thenThrow(rejected("Mã giảm giá đã hết hạn"));

        AppException ex = assertThrows(AppException.class, () -> orderService.createOrder(request("OLD", null)));

        assertEquals(ErrorCode.COUPON_INVALID, ex.getErrorCode());
        assertEquals("Mã giảm giá đã hết hạn", ex.getMessage(), "Khách phải thấy đúng lý do từ Coupon Service");
        assertEquals(0, orderRepository.count());
        assertEquals(0, taskRepository.count(), "Mã bị từ chối thì chưa trừ lượt, không có gì để hoàn");
        verify(inventoryClient, never()).decreaseStockBulk(any());
    }

    @Test
    void couponServiceDown_failsCleanly_andSchedulesReverse() {
        when(couponClient.redeem(any())).thenThrow(new RuntimeException("read timed out"));

        AppException ex = assertThrows(AppException.class, () -> orderService.createOrder(request("SALE10", null)));

        assertEquals(ErrorCode.COUPON_SERVICE_UNAVAILABLE, ex.getErrorCode());
        assertEquals(0, orderRepository.count());
        List<PendingTaskEntity> tasks = taskRepository.findAll();
        assertEquals(1, tasks.size());
        assertEquals(PendingTaskType.REVERSE_COUPON, tasks.get(0).getType());
    }

    @Test
    void stockFailureAfterCoupon_givesTheCouponBack() {
        couponGives("10");
        doThrow(new RuntimeException("inventory down")).when(inventoryClient).decreaseStockBulk(any());

        assertThrows(AppException.class, () -> orderService.createOrder(request("SALE10", null)));

        assertEquals(0, orderRepository.count());
        verify(couponClient).reverse(anyString());
        verify(pointClient, never()).reverse(anyString());
    }

    // ── Quy tắc 2: hủy / trả hàng hoàn mã ────────────────────────────────────

    @Test
    void rule2_cancel_returnsTheCoupon() {
        OrderEntity order = orderWithCoupon(OrderStatus.PENDING, "SALE10");

        orderService.cancelOrder(order.getId(), "1", "ADMIN");

        verify(couponClient).reverse(order.getId());
    }

    @Test
    void rule2_returnAfterDelivery_returnsTheCoupon() {
        OrderEntity order = orderWithCoupon(OrderStatus.DELIVERED, "SALE10");

        orderService.returnOrder(order.getId());

        assertEquals(OrderStatus.RETURNED, orderRepository.findById(order.getId()).orElseThrow().getStatus());
        verify(couponClient).reverse(order.getId());
    }

    @Test
    void cancelOrderWithoutCoupon_doesNotCallCouponService() {
        OrderEntity order = orderWithCoupon(OrderStatus.PENDING, null);

        orderService.cancelOrder(order.getId(), "1", "ADMIN");

        verifyNoInteractions(couponClient);
    }

    @Test
    void cancelWhileCouponServiceDown_orderStillCancelled_couponReturnedLater() {
        OrderEntity order = orderWithCoupon(OrderStatus.PENDING, "SALE10");
        doThrow(new RuntimeException("coupon-service down")).when(couponClient).reverse(order.getId());

        orderService.cancelOrder(order.getId(), "1", "ADMIN");

        assertEquals(OrderStatus.CANCELLED, orderRepository.findById(order.getId()).orElseThrow().getStatus());
        PendingTaskEntity task = taskRepository.findByTypeAndOrderId(PendingTaskType.REVERSE_COUPON, order.getId()).orElseThrow();
        assertEquals(PendingTaskEntity.Status.PENDING, task.getStatus());

        doNothing().when(couponClient).reverse(order.getId());
        task.setNextAttemptAt(LocalDateTime.now().minusSeconds(1));
        taskRepository.save(task);
        processor.run();

        assertEquals(PendingTaskEntity.Status.DONE,
                taskRepository.findByTypeAndOrderId(PendingTaskType.REVERSE_COUPON, order.getId()).orElseThrow().getStatus());
    }

    // ── Quy tắc 3: đơn có mã không tích điểm ─────────────────────────────────

    @Test
    void rule3_deliveredOrderWithCoupon_earnsNoPoints() {
        OrderEntity order = orderWithCoupon(OrderStatus.SHIPPING, "SALE10");

        orderService.deliverOrder(order.getId());

        assertEquals(OrderStatus.DELIVERED, orderRepository.findById(order.getId()).orElseThrow().getStatus());
        verify(kafkaTemplate, never()).send(eq("order-delivered-topic"), anyString());
        assertEquals(0, taskRepository.count());
    }

    @Test
    void rule3_deliveredOrderWithoutCoupon_stillEarnsPoints() {
        OrderEntity order = orderWithCoupon(OrderStatus.SHIPPING, null);

        orderService.deliverOrder(order.getId());

        verify(kafkaTemplate).send(eq("order-delivered-topic"), contains(order.getId()));
    }
}
