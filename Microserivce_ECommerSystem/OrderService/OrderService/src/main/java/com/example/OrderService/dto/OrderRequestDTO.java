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
}