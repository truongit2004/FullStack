package com.example.CouponService.repository;

import com.example.CouponService.entity.CouponEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CouponRepository extends JpaRepository<CouponEntity, String> {

    Optional<CouponEntity> findByCode(String code);

    boolean existsByCode(String code);

    List<CouponEntity> findAllByOrderByCreatedAtDesc();

    /**
     * Đọc mã và KHÓA dòng đó tới hết transaction. Hai khách cùng dùng lượt cuối cùng của một mã
     * sẽ lần lượt đi qua đây, nên chỉ một người được.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CouponEntity c where c.code = :code")
    Optional<CouponEntity> findByCodeForUpdate(@Param("code") String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CouponEntity c where c.id = :id")
    Optional<CouponEntity> findByIdForUpdate(@Param("id") String id);
}
