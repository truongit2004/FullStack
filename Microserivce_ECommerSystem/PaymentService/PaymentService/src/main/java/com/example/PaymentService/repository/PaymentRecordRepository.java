package com.example.PaymentService.repository;

import com.example.PaymentService.entity.PaymentRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRecordRepository extends JpaRepository<PaymentRecordEntity, String> {
    java.util.Optional<PaymentRecordEntity> findByOrderId(String orderId);

    java.util.Optional<PaymentRecordEntity> findFirstByOrderIdAndTransactionId(String orderId, String transactionId);

    java.util.List<PaymentRecordEntity> findTop50ByStatusAndOrderSyncedFalseOrderByCreatedAtAsc(String status);
}
