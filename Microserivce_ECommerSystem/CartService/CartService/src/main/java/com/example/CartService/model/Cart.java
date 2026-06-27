package com.example.CartService.model;

import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Lưu trong Redis với key: "cart:{userId}"
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Cart implements Serializable {

    private String userId;

    @Builder.Default
    private List<CartItem> items = new ArrayList<>();

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    // ── Tính tổng tiền ──────────────────────────────────────
    public BigDecimal getTotalAmount() {
        return items.stream()
                .map(CartItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public int getTotalItems() {
        return items.stream()
                .mapToInt(CartItem::getQuantity)
                .sum();
    }

    public void updateTimestamp() {
        this.updatedAt = LocalDateTime.now();
    }
}
