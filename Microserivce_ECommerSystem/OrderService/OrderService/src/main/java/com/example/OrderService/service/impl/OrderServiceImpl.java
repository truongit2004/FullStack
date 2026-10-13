package com.example.OrderService.service.impl;

import com.example.OrderService.client.CouponClient;
import com.example.OrderService.client.InventoryClient;
import com.example.OrderService.client.PointClient;
import com.example.OrderService.client.ProductClient;
import com.example.OrderService.client.ShippingClient;
import com.example.OrderService.client.UserClient;
import com.example.OrderService.dto.*;
import com.example.OrderService.entity.OrderEntity;
import com.example.OrderService.entity.OrderItemEntity;
import com.example.OrderService.entity.OrderStatus;
import com.example.OrderService.exception.AppException;
import com.example.OrderService.exception.ErrorCode;
import com.example.OrderService.mapper.OrderMapper;
import com.example.OrderService.repository.OrderRepository;
import com.example.OrderService.retry.OrderSideEffects;
import com.example.OrderService.retry.PendingTaskService;
import com.example.OrderService.retry.PendingTaskType;
import com.example.OrderService.service.OrderService;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final ProductClient productClient;
    private final InventoryClient inventoryClient;
    private final UserClient userClient;
    private final OrderMapper orderMapper;
    private final ShippingClient shippingClient;
    private final PointClient pointClient;
    private final CouponClient couponClient;
    private final OrderSideEffects sideEffects;
    private final PendingTaskService pendingTasks;
    private static final String ORDER_TOPIC = "order-placed-topic";
    private static final String EVENT_TYPE_PLACED = "ORDER_PLACED";
    private static final String EVENT_TYPE_RETURNED = "ORDER_RETURNED";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    // ─────────────────────────────────────────────
    // TẠO ĐƠN HÀNG
    // ─────────────────────────────────────────────
    @Override
    @Transactional
    public OrderResponseDTO createOrder(OrderRequestDTO request) {
        log.info("Creating order for userId: {}", request.getUserId());

        // 0. Mã giảm giá và điểm thưởng không dùng chung trong một đơn
        String couponCode = normalizeCouponCode(request.getCouponCode());
        boolean wantsPoints = request.getPointsToRedeem() != null
                && request.getPointsToRedeem().compareTo(BigDecimal.ZERO) > 0;
        if (couponCode != null && wantsPoints) {
            throw new AppException(ErrorCode.COUPON_AND_POINTS_NOT_COMBINABLE);
        }

        // 1. Validate User
        validateUser(request.getUserId());

        // 2. Map and Validate Items
        List<OrderItemEntity> items = buildOrderItems(request.getItems());

        // 3. Calculate Totals
        BigDecimal totalAmount = items.stream()
                .map(OrderItemEntity::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal shippingFee = resolveShippingFee(request);

        // 4. Create Order Entity (discount tạm = 0 — cần orderId thật đã được sinh (bước 6)
        //    trước khi gọi PointService, để tránh lệ thuộc vào việc Hibernate có giữ nguyên
        //    ID tự gán cho @GeneratedValue(strategy = GenerationType.UUID) hay không)
        OrderEntity order = OrderEntity.builder()
                .userId(request.getUserId())
                .totalAmount(totalAmount)
                .shippingFee(shippingFee)
                .discountAmount(BigDecimal.ZERO)
                .finalAmount(totalAmount.add(shippingFee))
                .shippingAddress(request.getShippingAddress())
                .note(request.getNote())
                .status(OrderStatus.PENDING)
                .deleted(false)
                .build();

        // 5. Link items to order
        items.forEach(item -> item.setOrder(order));
        order.setItems(items);

        // 6. Save Order (sinh orderId thật)
        OrderEntity savedOrder = orderRepository.save(order);
        String orderId = savedOrder.getId();

        // 7. Giảm giá: HOẶC mã giảm giá, HOẶC điểm tích lũy (tối đa 50% totalAmount) — đã chặn dùng cả hai ở bước 0
        boolean usedCoupon = couponCode != null;
        BigDecimal discountAmount = usedCoupon
                ? redeemCoupon(request.getUserId(), orderId, couponCode, totalAmount)
                : redeemPointsIfRequested(request, orderId, totalAmount);

        // Từ đây mã / điểm (nếu có) ĐÃ bị trừ: bất kỳ lỗi nào khiến đơn không tạo được đều phải hoàn lại
        try {
            if (usedCoupon) {
                savedOrder.setCouponCode(couponCode);
            }
            if (usedCoupon || discountAmount.compareTo(BigDecimal.ZERO) > 0) {
                savedOrder.setDiscountAmount(discountAmount);
                savedOrder.setFinalAmount(totalAmount.add(shippingFee).subtract(discountAmount));
                savedOrder = orderRepository.save(savedOrder);
            }

            // 8. Decrease Stock via Inventory Service
            decreaseStockForItems(items);
        } catch (RuntimeException e) {
            if (usedCoupon) {
                reverseCouponSafely(orderId, true);
            } else if (discountAmount.compareTo(BigDecimal.ZERO) > 0) {
                reversePointsSafely(orderId, true);
            }
            throw e;
        }

        // 9. Bắn thông báo Đặt hàng
        publishOrderEvent(savedOrder.getId(), savedOrder.getFinalAmount(), EVENT_TYPE_PLACED);

        log.info("✅ Order created successfully: orderId={}, userId={}", savedOrder.getId(), savedOrder.getUserId());
        return orderMapper.toResponse(savedOrder);
    }

    private void validateUser(String userId) {
        try {
            UserResponseDTO user = userClient.getUserById(userId);
            if (user == null) {
                throw new AppException(ErrorCode.USER_NOT_FOUND);
            }
        } catch (AppException e) {
            throw e;
        } catch (FeignException.NotFound e) {
            log.error("❌ User not found id={}: {}", userId, e.getMessage());
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        } catch (Exception e) {
            // User Service chết / quá tải / ngắt mạch: báo đúng nguyên nhân thay vì "không tồn tại"
            log.error("❌ User Service unavailable while validating user id={}: {}", userId, e.getMessage());
            throw new AppException(ErrorCode.USER_SERVICE_UNAVAILABLE);
        }
    }

    private List<OrderItemEntity> buildOrderItems(List<OrderItemDTO> itemDTOs) {
        return itemDTOs.stream().map(dto -> {
            ProductResponseDTO product = productClient.getProductById(dto.getProductId());

            if (product == null) {
                throw new AppException(ErrorCode.PRODUCT_NOT_FOUND);
            }
            if (!"ACTIVE".equals(product.getProductStatus())) {
                throw new AppException(ErrorCode.PRODUCT_NOT_AVAILABLE);
            }

            InventoryResponseDTO inventory = inventoryClient.getStock(dto.getProductId());
            if (inventory.getStockQuantity() < dto.getQuantity()) {
                log.warn("Insufficient stock for product id={}. Available: {}, Requested: {}",
                        dto.getProductId(), inventory.getStockQuantity(), dto.getQuantity());
                throw new AppException(ErrorCode.INSUFFICIENT_STOCK);
            }

            BigDecimal price = product.isOnSale() ? product.getDiscountedPrice() : product.getPrice();
            BigDecimal subtotal = price.multiply(BigDecimal.valueOf(dto.getQuantity()));

            return OrderItemEntity.builder()
                    .productId(product.getId())
                    .productName(product.getName())
                    .productImage(product.getImageUrl())
                    .price(price)
                    .quantity(dto.getQuantity())
                    .subtotal(subtotal)
                    .build();
        // Phải là danh sách sửa được: Hibernate cần thay nội dung collection khi đơn được lưu lại
        // (Stream.toList() trả danh sách bất biến -> lưu lần 2, ví dụ khi dùng điểm, ném UnsupportedOperationException)
        }).collect(Collectors.toCollection(java.util.ArrayList::new));
    }

    /**
     * Tính phí ship qua ShippingService dựa trên địa chỉ giao hàng.
     * Nếu ShippingService không phản hồi (lỗi mạng, chưa khởi động...), dùng
     * giá trị client gửi lên (hoặc 0) để không chặn luồng đặt hàng.
     */
    private BigDecimal resolveShippingFee(OrderRequestDTO request) {
        try {
            ShippingFeeResponseDTO feeResponse = shippingClient.getShippingFee(request.getShippingAddress());
            if (feeResponse != null && feeResponse.getFee() != null) {
                return feeResponse.getFee();
            }
        } catch (Exception e) {
            log.warn("Could not calculate shipping fee dynamically, falling back to client-provided value: {}", e.getMessage());
        }
        return request.getShippingFee() != null ? request.getShippingFee() : BigDecimal.ZERO;
    }

    /**
     * Áp dụng giảm giá bằng điểm tích lũy nếu khách yêu cầu (request.pointsToRedeem).
     * Gọi PointService trừ điểm ngay (đồng bộ); giới hạn dùng điểm theo tiền hàng
     * (point.max-redeem-ratio) do PointService kiểm tra — không hard-code ở đây.
     */
    private BigDecimal redeemPointsIfRequested(OrderRequestDTO request, String orderId, BigDecimal totalAmount) {
        BigDecimal pointsToRedeem = request.getPointsToRedeem();
        if (pointsToRedeem == null || pointsToRedeem.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        try {
            PointRedeemResponseDTO response = pointClient.redeem(PointRedeemRequestDTO.builder()
                    .userId(request.getUserId())
                    .orderId(orderId)
                    .points(pointsToRedeem)
                    .orderAmount(totalAmount)
                    .build());
            return response.getDiscountAmount();
        } catch (FeignException.BadRequest e) {
            // Point Service từ chối theo nghiệp vụ: vượt giới hạn hoặc không đủ điểm
            log.warn("❌ Không thể dùng điểm cho orderId {}: {}", orderId, e.getMessage());
            if (e.contentUTF8().contains(ErrorCode.POINTS_EXCEED_LIMIT.name())) {
                throw new AppException(ErrorCode.POINTS_EXCEED_LIMIT);
            }
            throw new AppException(ErrorCode.INSUFFICIENT_POINTS);
        } catch (Exception e) {
            // Point Service chết / quá thời gian / ngắt mạch. Có thể điểm ĐÃ bị trừ mà phản hồi không về kịp,
            // nên ghi lại việc hoàn điểm (không có gì để hoàn thì Point Service bỏ qua).
            log.error("❌ Point Service unavailable while redeeming for orderId {}: {}", orderId, e.getMessage());
            pendingTasks.enqueueNow(PendingTaskType.REVERSE_POINTS, orderId, "Dùng điểm lỗi: " + e.getMessage());
            throw new AppException(ErrorCode.POINT_SERVICE_UNAVAILABLE);
        }
    }

    private void decreaseStockForItems(List<OrderItemEntity> items) {
        Map<String, Integer> bulkItems = items.stream()
                .collect(Collectors.toMap(OrderItemEntity::getProductId, OrderItemEntity::getQuantity));
        try {
            inventoryClient.decreaseStockBulk(bulkItems);
        } catch (Exception e) {
            log.error("Failed to bulk decrease stock for items: {}", e.getMessage());
            throw new AppException(ErrorCode.UNCATEGORIZED);
        }
    }

    // ─────────────────────────────────────────────
    // CRUD CƠ BẢN
    // ─────────────────────────────────────────────
    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponseDTO> getAllOrders(Pageable pageable) {
        return orderRepository.findByDeletedFalse(pageable)
                .map(orderMapper::toResponse);
    }

    @Override
    public OrderResponseDTO getOrderById(String id) {
        return orderMapper.toResponse(findActiveOrder(id));
    }

    @Override
    @Transactional
    public OrderResponseDTO updateOrder(String id, OrderRequestDTO request) {
        OrderEntity order = findActiveOrder(id);

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new AppException(ErrorCode.ORDER_CANNOT_BE_MODIFIED);
        }

        order.setShippingAddress(request.getShippingAddress());
        order.setNote(request.getNote());

        return orderMapper.toResponse(orderRepository.save(order));
    }

    @Override
    @Transactional
    public void deleteOrder(String id) {
        OrderEntity order = findActiveOrder(id);
        order.setDeleted(true);
        order.setDeletedAt(LocalDateTime.now());
        orderRepository.save(order);
    }

    // ─────────────────────────────────────────────
    // CHUYỂN TRẠNG THÁI
    // ─────────────────────────────────────────────
    @Override
    @Transactional
    public OrderResponseDTO confirmOrder(String id) {
        OrderEntity order = findActiveOrder(id);
        validateTransition(order, OrderStatus.PENDING, OrderStatus.CONFIRMED);
        order.setStatus(OrderStatus.CONFIRMED);
        return orderMapper.toResponse(orderRepository.save(order));
    }

    @Override
    @Transactional
    public OrderResponseDTO shipOrder(String id) {
        OrderEntity order = findActiveOrder(id);
        
        // Nới lỏng kiểm tra trạng thái: cho phép chuyển sang SHIPPING từ bất kỳ trạng thái nào hợp lệ (PENDING, PAID, CONFIRMED)
        if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.DELIVERED) {
            throw new AppException(ErrorCode.INVALID_ORDER_STATUS_TRANSITION);
        }

        order.setStatus(OrderStatus.SHIPPING);
        OrderEntity savedOrder = orderRepository.save(order);

        try {
            sideEffects.createShippingIfMissing(savedOrder);
        } catch (Exception e) {
            // Không throw exception ở đây để không làm rollback việc update status của Order.
            // Vận đơn sẽ được tạo lại tự động khi Shipping Service hoạt động trở lại.
            log.error("❌ Could not create shipping ticket for order {}: {}", savedOrder.getId(), e.getMessage());
            pendingTasks.enqueue(PendingTaskType.CREATE_SHIPPING, savedOrder.getId(), e.getMessage());
        }

        return orderMapper.toResponse(savedOrder);
    }

    @Override
    @Transactional
    public OrderResponseDTO deliverOrder(String id) {
        OrderEntity order = findActiveOrder(id);
        validateTransition(order, OrderStatus.SHIPPING, OrderStatus.DELIVERED);
        order.setStatus(OrderStatus.DELIVERED);
        order.setDeliveredAt(LocalDateTime.now());
        OrderEntity saved = orderRepository.save(order);

        // Cộng điểm tích lũy (theo point.earn-rate x tiền hàng) — bất đồng bộ qua Kafka, không chặn luồng giao hàng
        // Đơn có dùng mã giảm giá thì không tích điểm
        if (saved.getCouponCode() == null) {
            publishPointEarnEvent(saved);
        }

        return orderMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public OrderResponseDTO cancelOrder(String id, String authId, String authRole) {
        OrderEntity order = findActiveOrder(id);

        if (authRole == null || !"ADMIN".equalsIgnoreCase(authRole)) {
            if (authId == null || !authId.equals(order.getUserId())) {
                log.error("❌ Unauthorized cancel attempt: orderId={}, requestBy={}", id, authId);
                throw new AppException(ErrorCode.UNAUTHORIZED);
            }
        }

        if (order.getStatus() == OrderStatus.DELIVERED || order.getStatus() == OrderStatus.REFUNDED) {
            throw new AppException(ErrorCode.ORDER_CANNOT_BE_CANCELLED);
        }

        if (order.getStatus() == OrderStatus.PAID) {
            log.warn("⚠️  Cancelling a PAID order id={}. A refund should be issued manually or via PaymentService.",
                    id);
        }

        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(LocalDateTime.now());
        OrderEntity saved = orderRepository.save(order);

        Map<String, Integer> bulkItems = saved.getItems().stream()
                .collect(Collectors.toMap(OrderItemEntity::getProductId, OrderItemEntity::getQuantity));
        inventoryClient.increaseStockBulk(bulkItems);

        // Hoàn lại điểm đã dùng / thu hồi điểm đã cộng cho đơn này (nếu có)
        reversePointsSafely(saved.getId());
        // Hoàn lượt dùng mã giảm giá của đơn này (nếu có)
        if (saved.getCouponCode() != null) {
            reverseCouponSafely(saved.getId(), false);
        }

        return orderMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public OrderResponseDTO refundOrder(String id) {
        OrderEntity order = findActiveOrder(id);
        validateTransition(order, OrderStatus.DELIVERED, OrderStatus.REFUNDED);
        order.setStatus(OrderStatus.REFUNDED);
        return orderMapper.toResponse(orderRepository.save(order));
    }

    @Override
    @Transactional
    public OrderResponseDTO returnOrder(String id) {
        OrderEntity order = findActiveOrder(id);

        // Cho phép hoàn hàng nếu đã thanh toán (PAID) hoặc đã giao hàng (DELIVERED)
        if (order.getStatus() != OrderStatus.PAID && order.getStatus() != OrderStatus.DELIVERED) {
            log.warn("⚠️ Order {} is not in PAID or DELIVERED status, cannot handle return.", id);
            throw new AppException(ErrorCode.INVALID_ORDER_STATUS_TRANSITION);
        }

        order.setStatus(OrderStatus.RETURNED);
        OrderEntity saved = orderRepository.save(order);

        // Hoàn lại kho (stock) vì hàng đã được trả lại
        Map<String, Integer> bulkItems = saved.getItems().stream()
                .collect(Collectors.toMap(OrderItemEntity::getProductId, OrderItemEntity::getQuantity));
        inventoryClient.increaseStockBulk(bulkItems);

        // Hoàn lại điểm đã dùng / thu hồi điểm đã cộng cho đơn này (nếu có)
        reversePointsSafely(saved.getId());
        // Hoàn lượt dùng mã giảm giá của đơn này (nếu có)
        if (saved.getCouponCode() != null) {
            reverseCouponSafely(saved.getId(), false);
        }

        // Bắn thông báo Hoàn trả hàng qua Kafka
        publishOrderEvent(saved.getId(), saved.getFinalAmount(), EVENT_TYPE_RETURNED);

        log.info("✅ Order {} returned successfully: status reset to PENDING and stock restored.", id);
        return orderMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void markOrderAsPaid(String id) {
        log.info("Marking order as paid: {}", id);
        OrderEntity order = orderRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
        // Payment Service có thể gọi lại nhiều lần và gọi TRỄ (thử lại sau khi Order Service chết),
        // nên chỉ chuyển PAID khi đơn còn đang chờ; các trường hợp khác không được ghi đè trạng thái.
        switch (order.getStatus()) {
            case PENDING, CONFIRMED -> {
                order.setStatus(OrderStatus.PAID);
                orderRepository.save(order);
            }
            case CANCELLED -> log.error(
                    "⚠️ Đơn {} đã HỦY nhưng vừa nhận xác nhận thanh toán — cần hoàn tiền thủ công cho khách.", id);
            default -> log.info("Đơn {} đang ở trạng thái {}, bỏ qua yêu cầu đánh dấu đã thanh toán (đã xử lý trước đó).",
                    id, order.getStatus());
        }
    }

    // ─────────────────────────────────────────────
    // FILTER & USER
    // ─────────────────────────────────────────────
    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponseDTO> getOrdersByUser(String userId, Pageable pageable) {
        return orderRepository.findByUserIdAndDeletedFalse(userId, pageable)
                .map(orderMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponseDTO> filterOrders(String userId, OrderStatus status,
            LocalDateTime fromDate, LocalDateTime toDate,
            BigDecimal minTotal, BigDecimal maxTotal,
            Pageable pageable) {
        return orderRepository.filterOrders(userId, status, fromDate, toDate, minTotal, maxTotal, pageable)
                .map(orderMapper::toResponse);
    }

    // ─────────────────────────────────────────────
    // HELPER
    // ─────────────────────────────────────────────
    private OrderEntity findActiveOrder(String id) {
        return orderRepository.findById(id)
                .filter(o -> !o.getDeleted())
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
    }

    private void validateTransition(OrderEntity order, OrderStatus required, OrderStatus next) {
        if (order.getStatus() != required) {
            throw new AppException(ErrorCode.INVALID_ORDER_STATUS_TRANSITION);
        }
    }

    @Override
    public boolean hasPurchasedProduct(String userId, String productId) {
        return orderRepository.existsByUserIdAndProductIdAndStatus(userId, productId);
    }

    private void publishPointEarnEvent(OrderEntity order) {
        try {
            sideEffects.publishPointEarn(order);
        } catch (Exception e) {
            // Kafka chết: không chặn việc giao hàng, sự kiện cộng điểm sẽ được phát lại tự động
            log.error("❌ Failed to publish point-earn event for orderId {}: {}", order.getId(), e.getMessage());
            pendingTasks.enqueue(PendingTaskType.PUBLISH_POINT_EARN, order.getId(), e.getMessage());
        }
    }

    private String normalizeCouponCode(String code) {
        return code == null || code.isBlank() ? null : code.trim().toUpperCase();
    }

    /**
     * Dùng mã giảm giá cho đơn: Coupon Service kiểm tra điều kiện, trừ lượt và trả về số tiền giảm.
     * Mã bị từ chối (hết hạn, hết lượt, chưa đủ đơn tối thiểu...) thì chuyển nguyên lý do cho khách.
     */
    private BigDecimal redeemCoupon(String userId, String orderId, String couponCode, BigDecimal totalAmount) {
        try {
            CouponDiscountResponseDTO response = couponClient.redeem(CouponRedeemRequestDTO.builder()
                    .userId(userId)
                    .orderId(orderId)
                    .code(couponCode)
                    .orderAmount(totalAmount)
                    .build());
            return response.getDiscountAmount();
        } catch (FeignException.BadRequest e) {
            log.warn("❌ Mã {} bị từ chối cho orderId {}: {}", couponCode, orderId, e.contentUTF8());
            throw new AppException(ErrorCode.COUPON_INVALID, extractMessage(e, ErrorCode.COUPON_INVALID.getMessage()));
        } catch (Exception e) {
            // Coupon Service chết / quá thời gian / ngắt mạch. Có thể lượt ĐÃ bị trừ mà phản hồi không về kịp,
            // nên ghi lại việc hoàn mã (không có gì để hoàn thì Coupon Service bỏ qua).
            log.error("❌ Coupon Service unavailable while redeeming for orderId {}: {}", orderId, e.getMessage());
            pendingTasks.enqueueNow(PendingTaskType.REVERSE_COUPON, orderId, "Dùng mã lỗi: " + e.getMessage());
            throw new AppException(ErrorCode.COUPON_SERVICE_UNAVAILABLE);
        }
    }

    // Lấy trường "message" trong JSON lỗi mà service kia trả về
    private String extractMessage(FeignException e, String fallback) {
        try {
            com.fasterxml.jackson.databind.JsonNode message = objectMapper.readTree(e.contentUTF8()).get("message");
            return message != null && !message.asText().isBlank() ? message.asText() : fallback;
        } catch (Exception parseError) {
            return fallback;
        }
    }

    private void reverseCouponSafely(String orderId, boolean outsideCurrentTransaction) {
        try {
            sideEffects.reverseCoupon(orderId);
        } catch (Exception e) {
            // Coupon Service chết: không chặn thao tác chính, lượt dùng mã sẽ được hoàn tự động khi service hoạt động lại
            log.error("❌ Không thể hoàn mã giảm giá cho orderId {}: {}", orderId, e.getMessage());
            if (outsideCurrentTransaction) {
                pendingTasks.enqueueNow(PendingTaskType.REVERSE_COUPON, orderId, e.getMessage());
            } else {
                pendingTasks.enqueue(PendingTaskType.REVERSE_COUPON, orderId, e.getMessage());
            }
        }
    }

    private void reversePointsSafely(String orderId) {
        reversePointsSafely(orderId, false);
    }

    /**
     * @param outsideCurrentTransaction true khi thao tác đang chạy sắp rollback (tạo đơn thất bại):
     *                                  việc cần làm lại phải được lưu trong transaction riêng
     */
    private void reversePointsSafely(String orderId, boolean outsideCurrentTransaction) {
        try {
            sideEffects.reversePoints(orderId);
        } catch (Exception e) {
            // Point Service chết: không chặn việc hủy/trả đơn, điểm sẽ được hoàn tự động khi service hoạt động lại
            log.error("❌ Không thể hoàn/thu hồi điểm cho orderId {}: {}", orderId, e.getMessage());
            if (outsideCurrentTransaction) {
                pendingTasks.enqueueNow(PendingTaskType.REVERSE_POINTS, orderId, e.getMessage());
            } else {
                pendingTasks.enqueue(PendingTaskType.REVERSE_POINTS, orderId, e.getMessage());
            }
        }
    }

    private void publishOrderEvent(String orderId, BigDecimal amount, String eventType) {
        try {
            OrderEventDTO event = OrderEventDTO.builder()
                    .orderId(orderId)
                    .amount(amount)
                    .type(eventType)
                    .build();

            String jsonEvent = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(ORDER_TOPIC, jsonEvent);
            log.info("✅ Published event {} for orderId {}", eventType, orderId);
        } catch (Exception e) {
            log.error("❌ Failed to publish event {} for orderId {}: {}", eventType, orderId, e.getMessage());
        }
    }
}