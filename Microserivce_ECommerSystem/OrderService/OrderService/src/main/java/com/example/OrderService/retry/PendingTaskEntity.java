package com.example.OrderService.retry;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Hàng đợi "việc còn nợ" lưu trong database: khi một lời gọi sang service khác thất bại,
 * việc đó được ghi lại ở đây và PendingTaskProcessor thử lại định kỳ cho tới khi xong.
 * Nhờ vậy lỗi tạm thời của service kia không làm mất việc (trước đây chỉ ghi log rồi bỏ qua).
 */
@Entity
@Table(name = "order_pending_task",
        uniqueConstraints = @UniqueConstraint(columnNames = {"type", "order_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingTaskEntity {

    public enum Status { PENDING, DONE, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PendingTaskType type;

    @Column(name = "order_id", nullable = false)
    private String orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.PENDING;

    @Builder.Default
    private int attempts = 0;

    @Column(length = 500)
    private String lastError;

    private LocalDateTime nextAttemptAt;

    @CreationTimestamp
    private LocalDateTime createdAt;

    private LocalDateTime finishedAt;
}
