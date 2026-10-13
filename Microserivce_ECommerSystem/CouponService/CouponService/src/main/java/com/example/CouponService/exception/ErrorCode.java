package com.example.CouponService.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    UNCATEGORIZED(500, "Lỗi không xác định"),
    INVALID_REQUEST(400, "Dữ liệu không hợp lệ"),

    // Lý do mã không dùng được — khách thấy đúng thông báo này
    COUPON_NOT_FOUND(400, "Mã giảm giá không tồn tại"),
    COUPON_INACTIVE(400, "Mã giảm giá đang tạm ngừng"),
    COUPON_NOT_STARTED(400, "Mã giảm giá chưa tới thời gian áp dụng"),
    COUPON_EXPIRED(400, "Mã giảm giá đã hết hạn"),
    COUPON_EXHAUSTED(400, "Mã giảm giá đã hết lượt sử dụng"),
    COUPON_USER_LIMIT_REACHED(400, "Bạn đã dùng hết số lần cho phép của mã này"),
    COUPON_MIN_ORDER_NOT_MET(400, "Đơn hàng chưa đạt giá trị tối thiểu để dùng mã"),

    // Quản trị
    COUPON_CODE_EXISTS(400, "Mã này đã tồn tại"),
    COUPON_ID_NOT_FOUND(404, "Không tìm thấy mã giảm giá"),
    COUPON_ALREADY_USED(400, "Mã đã có người dùng: không được đổi kiểu giảm, giá trị giảm hay chuỗi mã");

    private final int httpStatus;
    private final String message;
}
