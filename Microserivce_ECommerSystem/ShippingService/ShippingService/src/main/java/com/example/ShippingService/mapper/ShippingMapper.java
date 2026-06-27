package com.example.ShippingService.mapper;

import com.example.ShippingService.dto.ShippingRequestDTO;
import com.example.ShippingService.dto.ShippingResponseDTO;
import com.example.ShippingService.entity.ShippingEntity;

public class ShippingMapper {

    public static ShippingResponseDTO toResponse(ShippingEntity entity) {
        if (entity == null) {
            return null;
        }
        return ShippingResponseDTO.builder()
                .id(entity.getId())
                .orderId(entity.getOrderId())
                .trackingNumber(entity.getTrackingNumber())
                .carrier(entity.getCarrier())
                .status(entity.getStatus())
                .estimatedDeliveryDate(entity.getEstimatedDeliveryDate())
                .actualDeliveryDate(entity.getActualDeliveryDate())
                .recipientName(entity.getRecipientName())
                .recipientAddress(entity.getRecipientAddress())
                .recipientPhone(entity.getRecipientPhone())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
