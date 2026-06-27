//package com.example.OrderService.mapper;
//
//import com.example.OrderService.dto.OrderItemResponseDTO;
//import com.example.OrderService.dto.OrderResponseDTO;
//import com.example.OrderService.entity.OrderEntity;
//import com.example.OrderService.entity.OrderItemEntity;
//import org.springframework.stereotype.Component;
//
//import java.util.List;
//
//@Component
//public class OrderMapper {
//
//    public OrderResponseDTO toResponse(OrderEntity order) {
//        return OrderResponseDTO.builder()
//                .id(order.getId())
//                .userId(order.getUserId())
//
//                .status(order.getStatus())
//                .items(toItemResponseList(order.getItems()))
//                .totalAmount(order.getTotalAmount())
//                .shippingFee(order.getShippingFee())
//                .discountAmount(order.getDiscountAmount())
//                .finalAmount(order.getFinalAmount())
//                .shippingAddress(order.getShippingAddress())
//                .note(order.getNote())
//                .cancelledAt(order.getCancelledAt())
//                .deliveredAt(order.getDeliveredAt())
//                .createdAt(order.getCreatedAt())
//                .updatedAt(order.getUpdatedAt())
//                .build();
//    }
//
//    private List<OrderItemResponseDTO> toItemResponseList(List<OrderItemEntity> items) {
//        if (items == null) return List.of();
//        return items.stream().map(this::toItemResponse).toList();
//    }
//
//    private OrderItemResponseDTO toItemResponse(OrderItemEntity item) {
//        return OrderItemResponseDTO.builder()
//                .id(item.getId())
//                .productId(item.getProductId())
//                .productName(item.getProductName())
//                .productImage(item.getProductImage())
//                .price(item.getPrice())
//                .quantity(item.getQuantity())
//                .subtotal(item.getSubtotal())
//                .build();
//    }
//}
package com.example.OrderService.mapper;

import com.example.OrderService.dto.OrderItemDTO;
import com.example.OrderService.dto.OrderItemResponseDTO;
import com.example.OrderService.dto.OrderResponseDTO;
import com.example.OrderService.dto.ProductResponseDTO;
import com.example.OrderService.entity.OrderEntity;
import com.example.OrderService.entity.OrderItemEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class OrderMapper {

    // ─────────────────────────────────────────────
    // MAPPER CHO OUTPUT (Trả về cho người dùng)
    // ─────────────────────────────────────────────
    public OrderResponseDTO toResponse(OrderEntity order) {
        return OrderResponseDTO.builder()
                .id(order.getId())
                .userId(order.getUserId())
                .status(order.getStatus())
                .items(toItemResponseList(order.getItems()))
                .totalAmount(order.getTotalAmount())
                .shippingFee(order.getShippingFee())
                .discountAmount(order.getDiscountAmount())
                .finalAmount(order.getFinalAmount())
                .shippingAddress(order.getShippingAddress())
                .note(order.getNote())
                .cancelledAt(order.getCancelledAt())
                .deliveredAt(order.getDeliveredAt())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }

    private List<OrderItemResponseDTO> toItemResponseList(List<OrderItemEntity> items) {
        if (items == null) return List.of();
        return items.stream().map(this::toItemResponse).toList();
    }

    private OrderItemResponseDTO toItemResponse(OrderItemEntity item) {
        return OrderItemResponseDTO.builder()
                .id(item.getId())
                .productId(item.getProductId())
                .productName(item.getProductName())
                .productImage(item.getProductImage())
                .price(item.getPrice())
                .quantity(item.getQuantity())
                .subtotal(item.getSubtotal())
                .build();
    }

    // ─────────────────────────────────────────────
    // MAPPER CHO INPUT (Tạo Entity từ DTO của user)
    // ─────────────────────────────────────────────
    public OrderItemEntity toOrderItemEntity(OrderItemDTO dto, ProductResponseDTO product) {
        if (dto == null || product == null) {
            return null;
        }

        // Tính toán giá trị thực tế (xử lý logic giá sale)
        // Lưu ý: Sửa lại hàm isOnSale() và getDiscountedPrice() cho khớp với ProductResponseDTO của bạn
        BigDecimal effectivePrice = product.isOnSale()
                ? product.getDiscountedPrice()
                : product.getPrice();

        // Tính thành tiền = giá * số lượng
        BigDecimal subtotal = effectivePrice.multiply(BigDecimal.valueOf(dto.getQuantity()));

        return OrderItemEntity.builder()
                .productId(dto.getProductId()) // Lấy ID chuẩn xác từ DTO do người dùng gửi
                .productName(product.getName())
                .productImage(product.getImageUrl())
                .price(effectivePrice)
                .quantity(dto.getQuantity())
                .subtotal(subtotal)
                .build();
    }
}