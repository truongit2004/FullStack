package com.example.ProductService.entity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Data
@Setter
@Entity
@Builder
@Table(
        name = "product",
        indexes = {
                @Index(name = "idx_product_name", columnList = "name"),
                @Index(name = "idx_product_category", columnList = "categoryId"),
                @Index(name = "idx_product_status", columnList = "productStatus"),
                @Index(name = "idx_product_price", columnList = "price")
        }
)
public class ProductEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String name;
    private String imageUrl;
    private String description;
    private BigDecimal price;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "categoryId", insertable = false, updatable = false)
    private CategoryEntity category;

    private String categoryId;

    // --- Holiday Sale Properties ---
    private BigDecimal originalPrice;
    
    @Builder.Default
    private Boolean isOnSale = false;
    
    private Integer discountPercentage;

    /** ID của PromotionEvent đang áp dụng sale lên sản phẩm này (null = không có sale) */
    private String activeEventId;
    // -------------------------------

    @Builder.Default
    private Boolean deleted = false;

    private LocalDateTime deletedAt;     // ✅ thêm mới
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Enumerated(EnumType.STRING)
    private ProductStatus productStatus;

    @Builder.Default
    private Double averageRating = 0.0;

    @Builder.Default
    private Integer reviewCount = 0;

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