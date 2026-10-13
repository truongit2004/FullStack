package com.example.OrderService.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    // ── Lỗi chung ──────────────────────────────
    UNCATEGORIZED(500, "Lỗi không xác định"),
    INVALID_REQUEST(400, "Request không hợp lệ"),

    // ── Product ─────────────────────────────────
    PRODUCT_NOT_FOUND(404, "Sản phẩm không tồn tại"),
    PRODUCT_NOT_AVAILABLE(400, "Sản phẩm không còn kinh doanh"),
    INSUFFICIENT_STOCK(400, "Sản phẩm không đủ số lượng trong kho"),
    // Thêm mã lỗi này để sửa lỗi ở ProductClientFallback
    PRODUCT_SERVICE_UNAVAILABLE(503, "Dịch vụ Product đang tạm thời gián đoạn. Vui lòng thử lại sau!"),

    // ── User ───────────────────────────────────
    USER_NOT_FOUND(404, "Người dùng không tồn tại"),
    USER_SERVICE_UNAVAILABLE(503, "Dịch vụ tài khoản đang tạm thời gián đoạn. Vui lòng thử lại sau!"),
    POINT_SERVICE_UNAVAILABLE(503, "Dịch vụ điểm thưởng đang tạm thời gián đoạn. Vui lòng thử lại sau hoặc đặt hàng không dùng điểm."),
    SERVICE_UNAVAILABLE(503, "Hệ thống đang tạm thời gián đoạn. Vui lòng thử lại sau ít phút!"),

    // ── Order ───────────────────────────────────
    ORDER_NOT_FOUND(404, "Đơn hàng không tồn tại"),
    ORDER_CANNOT_BE_MODIFIED(400, "Chỉ đơn PENDING mới được chỉnh sửa"),
    ORDER_CANNOT_BE_CANCELLED(400, "Đơn đã giao hoặc đã hoàn tiền không thể hủy"),
    INVALID_ORDER_STATUS_TRANSITION(400, "Chuyển trạng thái đơn hàng không hợp lệ"),
    UNAUTHORIZED(403, "Bạn không có quyền thực hiện hành động này"),

    // ── Điểm tích lũy ────────────────────────────
    POINTS_EXCEED_LIMIT(400, "Số điểm sử dụng vượt quá giới hạn cho phép của đơn hàng"),
    INSUFFICIENT_POINTS(400, "Số điểm không đủ để sử dụng"),
    COUPON_INVALID(400, "Mã giảm giá không dùng được cho đơn hàng này"),
    COUPON_AND_POINTS_NOT_COMBINABLE(400, "Không thể dùng mã giảm giá cùng lúc với điểm thưởng. Vui lòng chọn một trong hai."),
    COUPON_SERVICE_UNAVAILABLE(503, "Dịch vụ mã giảm giá đang tạm thời gián đoạn. Vui lòng thử lại sau hoặc đặt hàng không dùng mã.");

    private final int httpStatus;
    private final String message;
}