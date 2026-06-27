package com.example.ProductService.service;

import com.example.ProductService.dto.ProductFilterDTO;
import com.example.ProductService.dto.ProductRequestDTO;
import com.example.ProductService.dto.ProductResponseDTO;
import org.springframework.data.domain.Page;

import java.util.List;

public interface ProductService {

    // Tạo sản phẩm
    ProductResponseDTO create(ProductRequestDTO dto);

    // Lấy sản phẩm theo id
    ProductResponseDTO getById(String id);

    // Cập nhật sản phẩm
    ProductResponseDTO update(String id, ProductRequestDTO dto);

    // Xóa mềm
    void softDelete(String id);

    // Xóa cứng
    void hardDelete(String id);

    List<ProductResponseDTO> getAllIncludeDeleted();   // tất cả
    List<ProductResponseDTO> getAllDeleted();
    Page<ProductResponseDTO> filter(ProductFilterDTO filter, int page, int size);

    void decreaseStock(String id, Integer quantity);
    void increaseStock(String id, Integer quantity);
}