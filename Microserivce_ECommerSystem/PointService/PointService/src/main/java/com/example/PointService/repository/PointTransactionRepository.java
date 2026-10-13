package com.example.PointService.repository;

import com.example.PointService.entity.PointTransactionEntity;
import com.example.PointService.entity.PointTransactionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PointTransactionRepository extends JpaRepository<PointTransactionEntity, String> {

    Optional<PointTransactionEntity> findByOrderIdAndType(String orderId, PointTransactionType type);

    List<PointTransactionEntity> findByOrderId(String orderId);

    List<PointTransactionEntity> findByUserIdOrderByCreatedAtDesc(String userId);
}
