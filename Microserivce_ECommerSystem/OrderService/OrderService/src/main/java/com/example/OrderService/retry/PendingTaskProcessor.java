package com.example.OrderService.retry;

import com.example.OrderService.entity.OrderEntity;
import com.example.OrderService.entity.OrderStatus;
import com.example.OrderService.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Định kỳ làm lại các việc còn nợ (xem PendingTaskEntity). */
@Slf4j
@Component
@RequiredArgsConstructor
public class PendingTaskProcessor {

    private final PendingTaskRepository repository;
    private final PendingTaskService taskService;
    private final OrderSideEffects sideEffects;
    private final OrderRepository orderRepository;

    @Scheduled(fixedDelayString = "${order.pending-task.interval-ms:30000}",
            initialDelayString = "${order.pending-task.initial-delay-ms:20000}")
    public void run() {
        List<PendingTaskEntity> due = repository
                .findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
                        PendingTaskEntity.Status.PENDING, LocalDateTime.now());

        for (PendingTaskEntity task : due) {
            try {
                execute(task);
                taskService.markDone(task);
                log.info("✅ Đã làm lại thành công {} cho đơn {} (sau {} lần lỗi)",
                        task.getType(), task.getOrderId(), task.getAttempts());
            } catch (Exception e) {
                taskService.markFailedAttempt(task, e.getMessage());
                log.warn("⏳ {} cho đơn {} vẫn chưa làm được (lần {}): {}",
                        task.getType(), task.getOrderId(), task.getAttempts(), e.getMessage());
            }
        }
    }

    private void execute(PendingTaskEntity task) throws Exception {
        switch (task.getType()) {
            case REVERSE_POINTS -> sideEffects.reversePoints(task.getOrderId());
            case REVERSE_COUPON -> sideEffects.reverseCoupon(task.getOrderId());
            case CREATE_SHIPPING -> {
                Optional<OrderEntity> order = orderRepository.findById(task.getOrderId());
                // Đơn không còn ở trạng thái đang giao (đã hủy, đã giao...) thì không cần vận đơn nữa
                if (order.isPresent() && order.get().getStatus() == OrderStatus.SHIPPING) {
                    sideEffects.createShippingIfMissing(order.get());
                }
            }
            case PUBLISH_POINT_EARN -> {
                Optional<OrderEntity> order = orderRepository.findById(task.getOrderId());
                // Đơn đã bị trả hàng trước khi kịp cộng điểm thì thôi không cộng; đơn có mã giảm giá không tích điểm
                if (order.isPresent() && order.get().getStatus() == OrderStatus.DELIVERED
                        && order.get().getCouponCode() == null) {
                    sideEffects.publishPointEarn(order.get());
                }
            }
        }
    }
}
