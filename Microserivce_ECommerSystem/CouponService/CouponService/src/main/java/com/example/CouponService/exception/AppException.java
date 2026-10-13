package com.example.CouponService.exception;

import lombok.Getter;

@Getter
public class AppException extends RuntimeException {

    private final ErrorCode errorCode;

    public AppException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /** Dùng khi thông báo cần kèm số liệu cụ thể (ví dụ còn thiếu bao nhiêu để đạt đơn tối thiểu). */
    public AppException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
