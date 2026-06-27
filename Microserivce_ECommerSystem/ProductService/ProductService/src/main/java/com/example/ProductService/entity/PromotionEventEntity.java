package com.example.ProductService.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "promotion_events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromotionEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String name; // e.g., "Black Friday Sale"
    
    private Integer discountPercentage; // e.g., 20
    
    private LocalDateTime startDate;
    private LocalDateTime endDate;

    // Status: PENDING, ACTIVE, COMPLETED
    @Builder.Default
    private String status = "PENDING";
}
