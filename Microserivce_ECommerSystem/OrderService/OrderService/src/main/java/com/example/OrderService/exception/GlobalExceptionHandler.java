package com.example.OrderService.exception;

import feign.FeignException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<Map<String, Object>> handleAppException(AppException ex) {
        ErrorCode code = ex.getErrorCode();
        return ResponseEntity
                .status(code.getHttpStatus())
                .body(Map.of(
                        "code",    code.getHttpStatus(),
                        "message", code.getMessage()
                ));
    }

    @ExceptionHandler(FeignException.class)
    public ResponseEntity<Map<String, Object>> handleFeignException(FeignException ex) {
        int status = ex.status();
        String message = "Lỗi gọi dịch vụ phụ thuộc";

        if (status == 401) {
            message = "Chưa xác thực khi gọi Product Service";
        } else if (status == 403) {
            message = "Không đủ quyền truy cập Product Service";
        } else if (status >= 500 || status == -1) {
            status = 503;
            message = ErrorCode.PRODUCT_SERVICE_UNAVAILABLE.getMessage();
        } else if (status > 0) {
            message = "Product Service trả về lỗi: HTTP " + status;
        } else {
            status = 500;
        }

        return ResponseEntity
                .status(status)
                .body(Map.of(
                        "code", status,
                        "message", message
                ));
    }
}
