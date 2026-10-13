package com.example.PointService.exception;

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
                        "code", code.getHttpStatus(),
                        "error", code.name(),
                        "message", code.getMessage()
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGlobalException(Exception ex) {
        return ResponseEntity
                .status(500)
                .body(Map.of(
                        "code", 500,
                        "message", "Lỗi không xác định: " + ex.getMessage()
                ));
    }
}
