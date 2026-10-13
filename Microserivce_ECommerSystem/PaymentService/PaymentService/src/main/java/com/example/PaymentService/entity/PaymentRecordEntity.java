package com.example.PaymentService.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_records")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PaymentRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String orderId;
    private BigDecimal amount;
    private String paymentMethod;
    private String transactionId; // VNPay Transaction ID
    private String responseCode; // 00 = Success
    private String status; // PENDING, SUCCESS, FAILED

    // Order Service đã được báo "đơn đã thanh toán" chưa. false = còn nợ, PaymentCompletionService sẽ tự thử lại.
    // null ở các bản ghi cũ (tạo trước khi có cột này) được coi là đã xong.
    private Boolean orderSynced;
    private Integer syncAttempts;
    @Column(length = 500)
    private String lastSyncError;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
