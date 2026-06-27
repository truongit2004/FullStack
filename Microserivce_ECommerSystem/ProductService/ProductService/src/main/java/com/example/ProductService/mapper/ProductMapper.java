package com.example.ProductService.mapper;

import com.example.ProductService.dto.ProductRequestDTO;
import com.example.ProductService.dto.ProductResponseDTO;
import com.example.ProductService.entity.ProductEntity;
import com.example.ProductService.entity.ProductStatus;
import lombok.experimental.UtilityClass;
//
//@Builder
//public class ProductMapper {
//
//    // Request → Entity
//    public static ProductEntity toEntity(ProductRequestDTO productRequestDTO, String imageUrl) {
//        return ProductEntity.builder()
//                .name(productRequestDTO.getName())
//                .description(productRequestDTO.getDescription())
//                .price(productRequestDTO.getPrice())
//                .categoryId(productRequestDTO.getCategoryId())
//                .stockQuantity(productRequestDTO.getStockQuantity())
//                .imageUrl(imageUrl) // ✅ lấy từ service
//                .productStatus(ProductStatus.ACTIVE)
//                .build();
//    }
//
//
//    // Entity → Response
//    public static ProductResponseDTO toResponse(ProductEntity productEntity, MultipartFile multipartFile) {
//        ProductResponseDTO productResponseDTO = new ProductResponseDTO();
//        productResponseDTO.setName(productEntity.getName());
//        productResponseDTO.setPrice(productEntity.getPrice());
//        productResponseDTO.setCategoryId(productEntity.getCategoryId());
//        productResponseDTO.setDescription(productEntity.getDescription());
//        productResponseDTO.setImageUrl(productEntity.getImageUrl());
//        productResponseDTO.setStockQuantity(productEntity.getStockQuantity());
//        return productResponseDTO;
//    }
//}
@UtilityClass  // ✅ thay @Builder — class toàn static method
public class ProductMapper {

    public static ProductEntity toEntity(ProductRequestDTO dto, String imageUrl) {
        return ProductEntity.builder()
                .name(dto.getName())
                .description(dto.getDescription())
                .price(dto.getPrice())
                .categoryId(dto.getCategoryId())
                .imageUrl(imageUrl)
                .productStatus(ProductStatus.ACTIVE)
                .build();
    }

    public static ProductResponseDTO toResponse(ProductEntity entity) {  // ✅ bỏ MultipartFile thừa
        return ProductResponseDTO.builder()
                .id(entity.getId())
                .name(entity.getName())
                .price(entity.getPrice())
                .originalPrice(entity.getOriginalPrice())
                .isOnSale(Boolean.TRUE.equals(entity.getIsOnSale()))
                .discountPercentage(entity.getDiscountPercentage())
                .categoryId(entity.getCategoryId())
                .categoryName(entity.getCategory() != null ? entity.getCategory().getName() : null)
                .description(entity.getDescription())
                .imageUrl(entity.getImageUrl())
                .productStatus(entity.getProductStatus() != null
                        ? entity.getProductStatus().name() : null)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())

                .build();
    }
}