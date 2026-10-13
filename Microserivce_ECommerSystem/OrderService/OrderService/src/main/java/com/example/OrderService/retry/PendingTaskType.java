package com.example.OrderService.retry;

/** Các việc phụ của đơn hàng cần được làm lại cho tới khi thành công nếu service kia đang chết. */
public enum PendingTaskType {
    REVERSE_POINTS,      // Hoàn điểm đã dùng / thu hồi điểm đã tích (gọi Point Service)
    REVERSE_COUPON,      // Hoàn lượt dùng mã giảm giá (gọi Coupon Service)
    CREATE_SHIPPING,     // Tạo vận đơn cho đơn đã chuyển SHIPPING (gọi Shipping Service)
    PUBLISH_POINT_EARN   // Phát sự kiện cộng điểm khi đơn DELIVERED (Kafka)
}
