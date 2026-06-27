package com.example.OrderService.service.impl;

import com.example.OrderService.client.InventoryClient;
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
import com.example.OrderService.service.OrderService;

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

    private static final String DEFAULT_CARRIER = "GHTK";
    private static final String DEFAULT_RECIPIENT = "Khách hàng";
    private static final String DEFAULT_PHONE = "N/A";

    private final OrderRepository orderRepository;
    private final ProductClient productClient;
    private final InventoryClient inventoryClient;
    private final UserClient userClient;
    private final OrderMapper orderMapper;
    private final ShippingClient shippingClient;
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

        // 1. Validate User
        validateUser(request.getUserId());

        // 2. Map and Validate Items
        List<OrderItemEntity> items = buildOrderItems(request.getItems());

        // 3. Calculate Totals
        BigDecimal totalAmount = items.stream()
                .map(OrderItemEntity::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal shippingFee = request.getShippingFee() != null ? request.getShippingFee() : BigDecimal.ZERO;
        BigDecimal discountAmount = request.getDiscountAmount() != null ? request.getDiscountAmount() : BigDecimal.ZERO;
        BigDecimal finalAmount = totalAmount.add(shippingFee).subtract(discountAmount);

        // 4. Create Order Entity
        OrderEntity order = OrderEntity.builder()
                .userId(request.getUserId())
                .totalAmount(totalAmount)
                .shippingFee(shippingFee)
                .discountAmount(discountAmount)
                .finalAmount(finalAmount)
                .shippingAddress(request.getShippingAddress())
                .note(request.getNote())
                .status(OrderStatus.PENDING)
                .deleted(false)
                .build();

        // 5. Link items to order
        items.forEach(item -> item.setOrder(order));
        order.setItems(items);

        // 6. Save Order
        OrderEntity savedOrder = orderRepository.save(order);

        // 7. Decrease Stock via Inventory Service
        decreaseStockForItems(items);

        // 8. Bắn thông báo Đặt hàng
        publishOrderEvent(savedOrder.getId(), savedOrder.getFinalAmount(), EVENT_TYPE_PLACED);

        log.info("✅ Order created successfully: orderId={}, userId={}", savedOrder.getId(), savedOrder.getUserId());
        return orderMapper.toResponse(savedOrder);
    }

    private void validateUser(String userId) {
        try {
            if (userClient.getUserById(userId) == null) {
                throw new AppException(ErrorCode.USER_NOT_FOUND);
            }
        } catch (Exception e) {
            log.error("❌ User not found id={}: {}", userId, e.getMessage());
            throw new AppException(ErrorCode.USER_NOT_FOUND);
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
        }).toList();
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
            // Kiểm tra xem đã có vận đơn chưa trước khi tạo mới
            boolean shippingExists = false;
            try {
                if (shippingClient.getByOrderId(id) != null) {
                    shippingExists = true;
                    log.info("ℹ️ Shipping ticket already exists for order: {}, skipping creation.", id);
                }
            } catch (Exception e) {
                // Nếu 404 hoặc lỗi thì coi như chưa có
                shippingExists = false;
            }

            if (!shippingExists) {
                String recipientName = DEFAULT_RECIPIENT;
                String recipientPhone = DEFAULT_PHONE;

                Object userObj = userClient.getUserById(order.getUserId());
                if (userObj instanceof Map<?, ?> map) {
                    if (map.get("username") != null)
                        recipientName = map.get("username").toString();
                    else if (map.get("email") != null)
                        recipientName = map.get("email").toString();
                }

                ShippingRequestDTO shippingRequest = ShippingRequestDTO.builder()
                        .orderId(savedOrder.getId())
                        .carrier(DEFAULT_CARRIER)
                        .recipientName(recipientName)
                        .recipientAddress(order.getShippingAddress() != null ? order.getShippingAddress() : "N/A")
                        .recipientPhone(recipientPhone)
                        .build();

                shippingClient.createShipping(shippingRequest);
                log.info("✅ Successfully created shipping ticket for order: {}", order.getId());
            }
        } catch (Exception e) {
            log.error("❌ Notification while syncing shipping: {}", e.getMessage());
            // Không throw exception ở đây để không làm rollback việc update status của Order
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
        return orderMapper.toResponse(orderRepository.save(order));
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
        order.setStatus(OrderStatus.PAID);
        orderRepository.save(order);
    }

    // ─────────────────────────────────────────────
    // FILTER & USER
    // ─────────────────────────────────────────────
    @Override
    public Page<OrderResponseDTO> getOrdersByUser(String userId, Pageable pageable) {
        return orderRepository.findByUserIdAndDeletedFalse(userId, pageable)
                .map(orderMapper::toResponse);
    }

    @Override
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

    private void publishOrderEvent(String orderId, BigDecimal amount, String eventType) {
        try {
            java.util.Map<String, String> event = new java.util.HashMap<>();
            event.put("orderId", orderId);
            event.put("amount", amount.toString());
            event.put("type", eventType);

            String jsonEvent = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(ORDER_TOPIC, jsonEvent);
            log.info("✅ Published event {} for orderId {}", eventType, orderId);
        } catch (Exception e) {
            log.error("❌ Failed to publish event {} for orderId {}: {}", eventType, orderId, e.getMessage());
        }
    }
}