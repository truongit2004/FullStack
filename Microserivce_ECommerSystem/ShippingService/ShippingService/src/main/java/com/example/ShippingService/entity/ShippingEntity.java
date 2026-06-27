package com.example.ShippingService.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "shippings")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ShippingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, unique = true)
    private String orderId;

    @Column(unique = true)
    private String trackingNumber;

    @Column(nullable = false)
    private String carrier; // e.g., VNPost, GHN, J&T

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShippingStatus status;

    private LocalDateTime estimatedDeliveryDate;
    private LocalDateTime actualDeliveryDate;

    // Recipient Info
    @Column(nullable = false)
    private String recipientName;
    @Column(nullable = false)
    private String recipientAddress;
    @Column(nullable = false)
    private String recipientPhone;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
