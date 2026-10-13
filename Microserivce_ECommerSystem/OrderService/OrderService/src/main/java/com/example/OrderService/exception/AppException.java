package com.example.OrderService.exception;

import lombok.Getter;

@Getter
public class AppException extends RuntimeException {

    private final ErrorCode errorCode;

    public AppException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /** Dùng khi cần chuyển nguyên thông báo cụ thể từ service khác (ví dụ lý do mã giảm giá bị từ chối). */
    public AppException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}