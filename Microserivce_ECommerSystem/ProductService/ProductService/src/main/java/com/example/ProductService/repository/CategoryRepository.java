package com.example.ProductService.repository;

import com.example.ProductService.entity.CategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<CategoryEntity, String> {
    Optional<CategoryEntity> findByName(String name);
    boolean existsByName(String name);

    @EntityGraph(attributePaths = "products")
    Optional<CategoryEntity> findWithProductsById(String id);
}
