package com.example.ShippingService.service;

import com.example.ShippingService.dto.ShippingRequestDTO;
import com.example.ShippingService.dto.ShippingResponseDTO;
import com.example.ShippingService.entity.ShippingStatus;

public interface ShippingService {
    ShippingResponseDTO createShipping(ShippingRequestDTO request);
    ShippingResponseDTO updateShippingStatus(String id, ShippingStatus status);
    ShippingResponseDTO getShippingByOrderId(String orderId);
    ShippingResponseDTO getShippingByTrackingNumber(String trackingNumber);
}
