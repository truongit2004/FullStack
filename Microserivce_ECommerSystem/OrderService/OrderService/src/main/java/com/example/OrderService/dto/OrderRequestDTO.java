package com.example.OrderService.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OrderRequestDTO {

    @NotBlank(message = "UserId không được để trống")
    private String userId;

    @NotEmpty(message = "Đơn hàng phải có ít nhất 1 sản phẩm")
    private List<OrderItemDTO> items;

    private String shippingAddress;
    private String note;
    private BigDecimal shippingFee;
    private BigDecimal discountAmount;
    private BigDecimal pointsToRedeem; // Số điểm khách muốn dùng để giảm giá đơn này (tối đa 50% totalAmount)
    private String couponCode;         // Mã giảm giá khách nhập. KHÔNG dùng chung với pointsToRedeem.
}