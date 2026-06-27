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

    // ── Order ───────────────────────────────────
    ORDER_NOT_FOUND(404, "Đơn hàng không tồn tại"),
    ORDER_CANNOT_BE_MODIFIED(400, "Chỉ đơn PENDING mới được chỉnh sửa"),
    ORDER_CANNOT_BE_CANCELLED(400, "Đơn đã giao hoặc đã hoàn tiền không thể hủy"),
    INVALID_ORDER_STATUS_TRANSITION(400, "Chuyển trạng thái đơn hàng không hợp lệ"),
    UNAUTHORIZED(403, "Bạn không có quyền thực hiện hành động này");

    private final int httpStatus;
    private final String message;
}