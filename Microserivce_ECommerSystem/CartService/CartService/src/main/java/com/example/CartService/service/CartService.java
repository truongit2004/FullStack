package com.example.CartService.service;

import com.example.CartService.client.OrderClient;
import com.example.CartService.client.ProductClient;
import com.example.CartService.dto.*;
import com.example.CartService.exception.CartException;
import com.example.CartService.model.Cart;
import com.example.CartService.model.CartItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartService {

    private final RedisTemplate<String, Cart> cartRedisTemplate;
    private final ProductClient productClient;
    private final com.example.CartService.client.InventoryClient inventoryClient;
    private final OrderClient orderClient;

    @Value("${cart.ttl-seconds:604800}")
    private long cartTtlSeconds;

    private static final String CART_KEY_PREFIX = "cart:";

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String cartKey(String userId) {
        return CART_KEY_PREFIX + userId;
    }

    private Cart getOrCreateCart(String userId) {
        if (userId == null) {
            throw new CartException("Người dùng chưa được xác thực.");
        }
        Cart cart = cartRedisTemplate.opsForValue().get(cartKey(userId));
        if (cart == null) {
            cart = Cart.builder().userId(userId).build();
        }
        // Đảm bảo items không null sau khi deserialize
        if (cart.getItems() == null) {
            cart.setItems(new java.util.ArrayList<>());
        }
        return cart;
    }

    private void saveCart(Cart cart) {
        cart.updateTimestamp();
        cartRedisTemplate.opsForValue().set(
                cartKey(cart.getUserId()), cart, cartTtlSeconds, TimeUnit.SECONDS);
    }

    // ── CRUD ─────────────────────────────────────────────────────────────────

    /** Lấy giỏ hàng của user và Tự động Cập nhật Giá (Chống giá lỏm) */
    public CartDTO.CartResponse getCart(String userId) {
        Cart cart = getOrCreateCart(userId);
        boolean cartUpdated = false;

        for (CartItem item : cart.getItems()) {
            try {
                // Kéo giá live từ ProductService (để bắt kịp nhịp Khuyến Mãi)
                ProductResponseDTO liveProduct = productClient.getProductById(item.getProductId());
                if (liveProduct != null && liveProduct.getPrice().compareTo(item.getPrice()) != 0) {
                    item.setPrice(liveProduct.getPrice());
                    cartUpdated = true;
                }
            } catch (Exception e) {
                log.warn("Không thể đồng bộ giá cho sản phẩm {} do ProductService lag", item.getProductId());
            }
        }

        if (cartUpdated) {
            saveCart(cart);
        }

        return toResponse(cart);
    }

    /** Thêm sản phẩm vào giỏ — nếu đã có thì cộng dồn số lượng */
    public CartDTO.CartResponse addItem(String userId, CartDTO.AddItemRequest request) {
        // 1. Lấy thông tin product (validate: tồn tại, ACTIVE, không bị xóa)
        ProductResponseDTO product = productClient.getProductById(request.getProductId());

        // 2. Kiểm tra tồn kho qua Inventory Service
        InventoryResponseDTO inventory = inventoryClient.getStock(request.getProductId());
        if (inventory == null) {
            throw new CartException("Không thể lấy thông tin tồn kho. Vui lòng thử lại.");
        }
        if (inventory.getStockQuantity() < request.getQuantity()) {
            throw new CartException(
                    "Sản phẩm '" + product.getName() + "' chỉ còn " +
                            inventory.getStockQuantity() + " trong kho.");
        }

        // 3. Thêm / cộng dồn vào giỏ
        Cart cart = getOrCreateCart(userId);

        Optional<CartItem> existing = cart.getItems().stream()
                .filter(i -> i.getProductId().equals(request.getProductId()))
                .findFirst();

        if (existing.isPresent()) {
            CartItem item = existing.get();
            int newQty = item.getQuantity() + request.getQuantity();
            if (inventory.getStockQuantity() < newQty) {
                throw new CartException(
                        "Tổng số lượng vượt quá tồn kho. Còn lại: " + inventory.getStockQuantity());
            }
            item.setQuantity(newQty);
        } else {
            cart.getItems().add(CartItem.builder()
                    .productId(product.getId())
                    .productName(product.getName())
                    .productImage(product.getImageUrl())
                    .price(product.getPrice()) // snapshot giá hiện tại
                    .quantity(request.getQuantity())
                    .build());
        }

        saveCart(cart);
        return toResponse(cart);
    }

    /** Cập nhật số lượng 1 item (ghi đè, không cộng dồn) */
    public CartDTO.CartResponse updateItem(String userId, String productId,
            CartDTO.UpdateItemRequest request) {
        Cart cart = getOrCreateCart(userId);

        CartItem item = cart.getItems().stream()
                .filter(i -> i.getProductId().equals(productId))
                .findFirst()
                .orElseThrow(() -> new CartException("Sản phẩm không có trong giỏ: " + productId));

        // Validate tồn kho lại khi update qua Inventory Service
        InventoryResponseDTO inventory = inventoryClient.getStock(productId);
        if (inventory.getStockQuantity() < request.getQuantity()) {
            throw new CartException(
                    "Số lượng vượt quá tồn kho. Còn lại: " + inventory.getStockQuantity());
        }

        item.setQuantity(request.getQuantity());
        saveCart(cart);
        return toResponse(cart);
    }

    /** Xóa 1 item khỏi giỏ (Giảm dần số lượng nếy > 1) */
    public CartDTO.CartResponse removeItem(String userId, String productId) {
        Cart cart = getOrCreateCart(userId);
        
        Optional<CartItem> itemOpt = cart.getItems().stream()
                .filter(i -> i.getProductId().equals(productId))
                .findFirst();

        if (itemOpt.isPresent()) {
            CartItem item = itemOpt.get();
            if (item.getQuantity() > 1) {
                // Nếu > 1 thì chỉ trừ đi 1 cái
                item.setQuantity(item.getQuantity() - 1);
            } else {
                // Nếu = 1 thì xóa hẳn bản ghi
                cart.getItems().remove(item);
            }
        } else {
            throw new CartException("Sản phẩm không có trong giỏ: " + productId);
        }

        saveCart(cart);
        return toResponse(cart);
    }

    /** Xóa nhiều item khỏi giỏ cùng lúc */
    public CartDTO.CartResponse removeItems(String userId, List<String> productIds) {
        Cart cart = getOrCreateCart(userId);
        cart.getItems().removeIf(item -> productIds.contains(item.getProductId()));
        saveCart(cart);
        return toResponse(cart);
    }

    /** Xóa toàn bộ giỏ hàng (Làm trống) */
    public CartDTO.CartResponse clearCart(String userId) {
        cartRedisTemplate.delete(cartKey(userId));
        // Trả về một giỏ hàng mới trống rỗng cho user
        return toResponse(Cart.builder().userId(userId).build());
    }

    // ── Checkout ─────────────────────────────────────────────────────────────

    /**
     * Checkout:
     * 1. Validate tất cả items (stock) sơ bộ
     * 2. Tạo Order bên Order Service (Order Service sẽ xử lý trừ kho và transaction)
     * 3. Xóa cart
     * 4. Trả về orderId
     */
    public String checkout(String userId, CartDTO.CheckoutRequest request, String jwtToken) {
        Cart cart = getOrCreateCart(userId);

        if (cart.getItems().isEmpty()) {
            throw new CartException("Giỏ hàng đang trống.");
        }

        // ── Bước 1: Validate sơ bộ (để báo lỗi sớm cho Web/App) ────────────
        for (CartItem item : cart.getItems()) {
            InventoryResponseDTO inventory = inventoryClient.getStock(item.getProductId());
            if (inventory.getStockQuantity() < item.getQuantity()) {
                throw new CartException(
                        "Sản phẩm '" + item.getProductName() + "' không đủ hàng trong kho. Còn lại: " +
                                inventory.getStockQuantity());
            }
        }

        // ── Bước 2: Tạo Order ────────────────────────────────────────────────
        String orderId;
        try {
            List<OrderItemDTO> orderItems = cart.getItems().stream()
                    .map(item -> OrderItemDTO.builder()
                            .productId(item.getProductId())
                            .productName(item.getProductName())
                            .productImage(item.getProductImage())
                            .price(item.getPrice())
                            .quantity(item.getQuantity())
                            .subtotal(item.getSubtotal())
                            .build())
                    .collect(Collectors.toList());

            OrderRequestDTO orderRequest = OrderRequestDTO.builder()
                    .userId(userId)
                    .items(orderItems)
                    .shippingAddress(request.getShippingAddress())
                    .note(request.getNote())
                    .shippingFee(request.getShippingFee())
                    .discountAmount(request.getDiscountAmount())
                    .build();

            // OrderService sẽ thực hiện trừ kho trong Transaction của nó
            orderId = orderClient.createOrder(orderRequest, jwtToken);

        } catch (Exception e) {
            log.error("Tạo đơn hàng thất bại: {}", e.getMessage());
            throw new CartException("Không thể tạo đơn hàng: " + e.getMessage());
        }

        // ── Bước 3: Xóa cart ─────────────────────────────────────────────────
        clearCart(userId);

        log.info("Checkout thành công. userId={} orderId={}", userId, orderId);
        return orderId;
    }

    // ── Mapper ───────────────────────────────────────────────────────────────

    private CartDTO.CartResponse toResponse(Cart cart) {
        List<CartDTO.CartItemResponse> itemResponses = cart.getItems().stream()
                .map(item -> CartDTO.CartItemResponse.builder()
                        .productId(item.getProductId())
                        .productName(item.getProductName())
                        .productImage(item.getProductImage())
                        .price(item.getPrice())
                        .quantity(item.getQuantity())
                        .subtotal(item.getSubtotal())
                        .build())
                .collect(Collectors.toList());

        return CartDTO.CartResponse.builder()
                .userId(cart.getUserId())
                .items(itemResponses)
                .totalAmount(cart.getTotalAmount())
                .totalItems(cart.getTotalItems())
                .updatedAt(cart.getUpdatedAt())
                .build();
    }
}
