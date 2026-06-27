package com.example.ProductService.repository;

import com.example.ProductService.entity.ProductEntity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
//public interface ProductRepository extends JpaRepository<ProductEntity,String> {
//    List<ProductEntity> findByDeletedTrue();
//}
public interface ProductRepository extends JpaRepository<ProductEntity, String>, JpaSpecificationExecutor<ProductEntity> {

    List<ProductEntity> findAllByDeletedFalse();

    Optional<ProductEntity> findByIdAndDeletedFalse(String id);

//    List<ProductEntity> findAll();
// Thêm method mới, xóa cái cũ
    Page<ProductEntity> findAllByDeletedFalse(Pageable pageable);
    // Lấy chỉ những sản phẩm đã xóa mềm
    List<ProductEntity> findAllByDeletedTrue();
}