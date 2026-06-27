package com.example.ProductService.specification;

import com.example.ProductService.dto.ProductFilterDTO;
import com.example.ProductService.entity.ProductEntity;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class ProductSpecification {

    public static Specification<ProductEntity> filter(ProductFilterDTO filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Chỉ lấy bản ghi chưa xóa (xử lý cả trường hợp NULL)
            predicates.add(cb.or(
                    cb.isNull(root.get("deleted")),
                    cb.isFalse(root.get("deleted"))
            ));

            if (filter.getName() != null && !filter.getName().isBlank()) {
                predicates.add(cb.like(
                        cb.lower(root.get("name")),
                        "%" + filter.getName().toLowerCase() + "%"
                ));
            }

            if (filter.getCategoryId() != null && !filter.getCategoryId().isBlank()) {
                predicates.add(cb.equal(root.get("categoryId"), filter.getCategoryId()));
            }

            if (filter.getMinPrice() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("price"), filter.getMinPrice()));
            }

            if (filter.getMaxPrice() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("price"), filter.getMaxPrice()));
            }

            if (filter.getMinStock() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("stockQuantity"), filter.getMinStock()));
            }

            if (filter.getMaxStock() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("stockQuantity"), filter.getMaxStock()));
            }

            if (filter.getProductStatus() != null && !filter.getProductStatus().isBlank()) {
                try {
                    com.example.ProductService.entity.ProductStatus status =
                            com.example.ProductService.entity.ProductStatus.valueOf(filter.getProductStatus().toUpperCase());
                    predicates.add(cb.equal(root.get("productStatus"), status));
                } catch (IllegalArgumentException e) {
                    // Nếu giá trị truyền lên không khớp với Enum, có thể bỏ qua hoặc xử lý tùy nghiệp vụ
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}