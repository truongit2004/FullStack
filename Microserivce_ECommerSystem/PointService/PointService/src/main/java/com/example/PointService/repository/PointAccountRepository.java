package com.example.PointService.repository;

import com.example.PointService.entity.PointAccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointAccountRepository extends JpaRepository<PointAccountEntity, String> {
}
