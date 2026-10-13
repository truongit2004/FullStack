package com.example.OrderService.retry;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class PendingTaskService {

    // Sau ngần này lần thử vẫn lỗi thì dừng, chờ người xử lý (khoảng 5 tiếng với lịch thử lại bên dưới)
    static final int MAX_ATTEMPTS = 40;

    private final PendingTaskRepository repository;

    /**
     * Ghi việc cần làm lại, trong CÙNG transaction với thao tác đang chạy:
     * thao tác rollback thì việc này cũng không được ghi.
     */
    @Transactional
    public void enqueue(PendingTaskType type, String orderId, String reason) {
        save(type, orderId, reason);
    }

    /**
     * Ghi việc cần làm lại trong transaction RIÊNG: vẫn được lưu kể cả khi thao tác đang chạy rollback
     * (ví dụ tạo đơn thất bại nhưng điểm đã bị trừ thì vẫn phải hoàn).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enqueueNow(PendingTaskType type, String orderId, String reason) {
        save(type, orderId, reason);
    }

    private void save(PendingTaskType type, String orderId, String reason) {
        PendingTaskEntity task = repository.findByTypeAndOrderId(type, orderId)
                .orElseGet(() -> PendingTaskEntity.builder().type(type).orderId(orderId).build());
        // Việc cũ đã xong mà lại phát sinh lần nữa (hoặc đã bỏ cuộc) thì mở lại
        task.setStatus(PendingTaskEntity.Status.PENDING);
        task.setLastError(truncate(reason));
        task.setNextAttemptAt(LocalDateTime.now().plusSeconds(30));
        task.setFinishedAt(null);
        repository.save(task);
        log.warn("⏳ Ghi nhận việc cần làm lại {} cho đơn {}: {}", type, orderId, reason);
    }

    @Transactional
    public void markDone(PendingTaskEntity task) {
        task.setStatus(PendingTaskEntity.Status.DONE);
        task.setLastError(null);
        task.setFinishedAt(LocalDateTime.now());
        repository.save(task);
    }

    @Transactional
    public void markFailedAttempt(PendingTaskEntity task, String error) {
        task.setAttempts(task.getAttempts() + 1);
        task.setLastError(truncate(error));
        if (task.getAttempts() >= MAX_ATTEMPTS) {
            task.setStatus(PendingTaskEntity.Status.FAILED);
            task.setFinishedAt(LocalDateTime.now());
            log.error("❌ BỎ CUỘC sau {} lần thử: {} cho đơn {} — cần xử lý thủ công. Lỗi cuối: {}",
                    task.getAttempts(), task.getType(), task.getOrderId(), error);
        } else {
            task.setNextAttemptAt(LocalDateTime.now().plus(backoff(task.getAttempts())));
        }
        repository.save(task);
    }

    // 30s, 1 phút, 2 phút, 4 phút, rồi giữ ở 8 phút
    static Duration backoff(int attempts) {
        long seconds = 30L << Math.min(Math.max(attempts - 1, 0), 4);
        return Duration.ofSeconds(seconds);
    }

    private String truncate(String text) {
        if (text == null) return null;
        return text.length() > 500 ? text.substring(0, 500) : text;
    }
}
