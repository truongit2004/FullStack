package com.example.PointService.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    UNCATEGORIZED(500, "Lỗi không xác định"),
    INSUFFICIENT_POINTS(400, "Số điểm không đủ để sử dụng"),
    POINTS_EXCEED_LIMIT(400, "Số điểm sử dụng vượt quá giới hạn cho phép của đơn hàng");

    private final int httpStatus;
    private final String message;
}
