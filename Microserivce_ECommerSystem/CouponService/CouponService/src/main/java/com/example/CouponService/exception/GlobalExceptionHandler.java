package com.example.CouponService.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
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
                        "message", ex.getMessage()
                ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse(ErrorCode.INVALID_REQUEST.getMessage());
        return ResponseEntity
                .status(400)
                .body(Map.of(
                        "code", 400,
                        "error", ErrorCode.INVALID_REQUEST.name(),
                        "message", message
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGlobalException(Exception ex) {
        return ResponseEntity
                .status(500)
                .body(Map.of(
                        "code", 500,
                        "error", ErrorCode.UNCATEGORIZED.name(),
                        "message", "Lỗi không xác định: " + ex.getMessage()
                ));
    }
}
