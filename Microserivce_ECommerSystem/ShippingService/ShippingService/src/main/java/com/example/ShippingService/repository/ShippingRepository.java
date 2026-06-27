package com.example.ShippingService.repository;

import com.example.ShippingService.entity.ShippingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ShippingRepository extends JpaRepository<ShippingEntity, String> {
    Optional<ShippingEntity> findByOrderId(String orderId);
    Optional<ShippingEntity> findByTrackingNumber(String trackingNumber);
}
