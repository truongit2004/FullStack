package com.example.CartService.model;

import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CartItem implements Serializable {

    private String productId;
    private String productName;   // snapshot tại thời điểm thêm vào giỏ
    private String productImage;  // snapshot
    private BigDecimal price;     // snapshot — giá tại lúc thêm vào giỏ

    private Integer quantity;

    public BigDecimal getSubtotal() {
        if (price == null || quantity == null) return BigDecimal.ZERO;
        return price.multiply(BigDecimal.valueOf(quantity));
    }
}
