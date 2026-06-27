package com.example.ShippingService.controller;

import com.example.ShippingService.dto.ShippingRequestDTO;
import com.example.ShippingService.dto.ShippingResponseDTO;
import com.example.ShippingService.dto.ShippingStatusUpdateDTO;
import com.example.ShippingService.service.ShippingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/shipping")
@RequiredArgsConstructor
public class ShippingController {

    private final ShippingService shippingService;

    @PostMapping
    public ResponseEntity<ShippingResponseDTO> createShipping(@Valid @RequestBody ShippingRequestDTO request) {
        return new ResponseEntity<>(shippingService.createShipping(request), HttpStatus.CREATED);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ShippingResponseDTO> updateStatus(
            @PathVariable String id,
            @Valid @RequestBody ShippingStatusUpdateDTO request) {
        return ResponseEntity.ok(shippingService.updateShippingStatus(id, request.getStatus()));
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<ShippingResponseDTO> getByOrderId(@PathVariable String orderId) {
        return ResponseEntity.ok(shippingService.getShippingByOrderId(orderId));
    }

    @GetMapping("/tracking/{trackingNumber}")
    public ResponseEntity<ShippingResponseDTO> getByTrackingNumber(@PathVariable String trackingNumber) {
        return ResponseEntity.ok(shippingService.getShippingByTrackingNumber(trackingNumber));
    }
}
