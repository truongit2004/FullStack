package com.example.CouponService.repository;

import com.example.CouponService.entity.CouponUsageEntity;
import com.example.CouponService.entity.CouponUsageStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CouponUsageRepository extends JpaRepository<CouponUsageEntity, String> {

    Optional<CouponUsageEntity> findByOrderId(String orderId);

    boolean existsByCouponId(String couponId);

    long countByCouponIdAndUserIdAndStatus(String couponId, String userId, CouponUsageStatus status);
}
