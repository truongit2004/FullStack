package com.example.User.Service.dto.external;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OrderResponseDTO {
    private String id;
    private String userId;
    private String status; // Kept as String to avoid enum binding errors
    private List<OrderItemResponseDTO> items;
    private BigDecimal totalAmount;
    private BigDecimal shippingFee;
    private BigDecimal discountAmount;
    private BigDecimal finalAmount;
    private String shippingAddress;
    private String note;
    private LocalDateTime cancelledAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
