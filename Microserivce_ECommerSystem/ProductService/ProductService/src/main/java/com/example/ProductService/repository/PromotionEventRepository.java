package com.example.ProductService.repository;

import com.example.ProductService.entity.PromotionEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PromotionEventRepository extends JpaRepository<PromotionEventEntity, String> {
    List<PromotionEventEntity> findByStatusIn(List<String> statuses);
}
