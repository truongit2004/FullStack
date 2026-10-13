package com.example.PaymentService.exception;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Mạch đang ngắt: service phụ thuộc vừa lỗi liên tục nên tạm ngừng gọi tới nó.
 * Trả 503 ngay với thông báo rõ ràng, thay vì lỗi 500 chung chung.
 * Đặt ưu tiên cao nhất để không bị handler RuntimeException chung bắt trước.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ResilienceExceptionHandler {

    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<Map<String, Object>> handleCircuitOpen(CallNotPermittedException ex) {
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "code", 503,
                        "status", 503,
                        "message", "Hệ thống đang tạm thời gián đoạn. Vui lòng thử lại sau ít phút!"
                ));
    }
}
