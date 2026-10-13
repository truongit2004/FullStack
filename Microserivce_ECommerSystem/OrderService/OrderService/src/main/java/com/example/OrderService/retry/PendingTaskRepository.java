package com.example.OrderService.retry;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PendingTaskRepository extends JpaRepository<PendingTaskEntity, String> {

    Optional<PendingTaskEntity> findByTypeAndOrderId(PendingTaskType type, String orderId);

    List<PendingTaskEntity> findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
            PendingTaskEntity.Status status, LocalDateTime now);
}
