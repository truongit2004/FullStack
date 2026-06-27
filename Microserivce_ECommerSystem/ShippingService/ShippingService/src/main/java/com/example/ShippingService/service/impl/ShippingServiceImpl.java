package com.example.ShippingService.service.impl;

import com.example.ShippingService.client.OrderClient;
import com.example.ShippingService.dto.ExternalOrderResponseDTO;
import com.example.ShippingService.dto.ShippingRequestDTO;
import com.example.ShippingService.dto.ShippingResponseDTO;
import com.example.ShippingService.entity.ShippingEntity;
import com.example.ShippingService.entity.ShippingStatus;
import com.example.ShippingService.exception.ResourceNotFoundException;
import com.example.ShippingService.mapper.ShippingMapper;
import com.example.ShippingService.repository.ShippingRepository;
import com.example.ShippingService.service.ShippingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShippingServiceImpl implements ShippingService {

    private final ShippingRepository shippingRepository;
    private final OrderClient orderClient;

    @Override
    @Transactional
    public ShippingResponseDTO createShipping(ShippingRequestDTO request) {
        if (shippingRepository.findByOrderId(request.getOrderId()).isPresent()) {
            throw new RuntimeException("Shipping assignment already exists for Order ID: " + request.getOrderId());
        }

        validateOrderForShipping(request.getOrderId());

        // Tạo Tracking Number ngẫu nhiên
        String trackingCode = request.getCarrier().toUpperCase() + "-" + 
                             UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        ShippingEntity entity = ShippingEntity.builder()
                .orderId(request.getOrderId())
                .trackingNumber(trackingCode)
                .carrier(request.getCarrier())
                .status(ShippingStatus.PENDING)
                .estimatedDeliveryDate(LocalDateTime.now().plusDays(3))
                .recipientName(request.getRecipientName())
                .recipientAddress(request.getRecipientAddress())
                .recipientPhone(request.getRecipientPhone())
                .build();

        log.info("Creating new shipping: orderId={}, tracking={}", request.getOrderId(), trackingCode);
        return ShippingMapper.toResponse(shippingRepository.save(entity));
    }

    @Override
    @Transactional
    public ShippingResponseDTO updateShippingStatus(String id, ShippingStatus status) {
        ShippingEntity entity = shippingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Shipping record not found: " + id));

        log.info("Updating shipping status: id={}, from={} to={}", id, entity.getStatus(), status);
        entity.setStatus(status);

        if (status == ShippingStatus.DELIVERED) {
            entity.setActualDeliveryDate(LocalDateTime.now());
        }

        syncStatusWithOrderService(entity.getOrderId(), status);

        return ShippingMapper.toResponse(shippingRepository.save(entity));
    }

    @Override
    public ShippingResponseDTO getShippingByOrderId(String orderId) {
        return shippingRepository.findByOrderId(orderId)
                .map(ShippingMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("No shipping record for Order ID: " + orderId));
    }

    @Override
    public ShippingResponseDTO getShippingByTrackingNumber(String trackingNumber) {
        return shippingRepository.findByTrackingNumber(trackingNumber)
                .map(ShippingMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("No shipping record for Tracking Number: " + trackingNumber));
    }

    // --- Private Helper Methods ---

    private void validateOrderForShipping(String orderId) {
        try {
            ExternalOrderResponseDTO order = orderClient.getOrderById(orderId);
            String status = order.getStatus();
            if (status != null && (status.equalsIgnoreCase("CANCELLED") || status.equalsIgnoreCase("RETURNED"))) {
                throw new RuntimeException("Cannot initiate shipping for " + status + " orders.");
            }
        } catch (Exception e) {
            log.error("Validation failed for orderId {}: {}", orderId, e.getMessage());
            throw new RuntimeException("Order validation failed. Please check if Order Service is online.");
        }
    }

    private void syncStatusWithOrderService(String orderId, ShippingStatus status) {
        try {
            switch (status) {
                case SHIPPED -> orderClient.shipOrder(orderId);
                case DELIVERED -> orderClient.deliverOrder(orderId);
                case CANCELED -> orderClient.cancelOrder(orderId);
                case RETURNED -> orderClient.returnOrder(orderId);
                default -> log.debug("Status {} does not require sync with OrderService", status);
            }
            log.info("Successfully synced status {} to OrderService for orderId {}", status, orderId);
        } catch (Exception e) {
            log.error("Failed to sync status {} to OrderService: {}", status, e.getMessage());
        }
    }
}
