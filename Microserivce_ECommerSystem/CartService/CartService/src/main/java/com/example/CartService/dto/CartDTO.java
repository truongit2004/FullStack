package com.example.CartService.dto;

import jakarta.validation.constraints.*;
import lombok.*;

// ── Request: thêm / cập nhật item ──────────────────────────
public class CartDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddItemRequest {
        @NotBlank(message = "productId không được để trống")
        private String productId;

        @Min(value = 1, message = "Số lượng phải >= 1")
        private Integer quantity;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateItemRequest {
        @Min(value = 1, message = "Số lượng phải >= 1")
        private Integer quantity;
    }

    // ── Request: checkout ───────────────────────────────────
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CheckoutRequest {
        @NotBlank(message = "Địa chỉ giao hàng không được để trống")
        private String shippingAddress;
        
        private String note;
        
        @NotNull(message = "Phí vận chuyển không được để trống")
        private java.math.BigDecimal shippingFee;
        
        @NotNull(message = "Số tiền giảm giá không được để trống")
        private java.math.BigDecimal discountAmount;

        // Số điểm thưởng muốn dùng cho đơn này (tuỳ chọn) — Order Service kiểm tra giới hạn và trừ điểm
        private java.math.BigDecimal pointsToRedeem;

        // Mã giảm giá khách nhập (tuỳ chọn) — Order Service kiểm tra và áp dụng; không dùng chung với điểm
        private String couponCode;
    }

    // ── Response trả về client ──────────────────────────────
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CartResponse {
        private String userId;
        private java.util.List<CartItemResponse> items;
        private java.math.BigDecimal totalAmount;
        private int totalItems;
        private java.time.LocalDateTime updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CartItemResponse {
        private String productId;
        private String productName;
        private String productImage;
        private java.math.BigDecimal price;
        private Integer quantity;
        private java.math.BigDecimal subtotal;
    }
}
