package com.example.PointService.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "point_account")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PointAccountEntity {

    @Id
    private String userId;

    @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
