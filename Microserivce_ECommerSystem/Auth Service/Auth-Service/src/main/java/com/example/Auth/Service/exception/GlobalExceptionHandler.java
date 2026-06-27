package com.example.Auth.Service.exception;

import com.example.Auth.Service.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @ExceptionHandler(feign.FeignException.class)
    public ResponseEntity<ErrorResponse> handleFeignException(feign.FeignException ex, HttpServletRequest request) {
        String message = ex.contentUTF8();
        // Sử dụng Jackson ObjectMapper để trích xuất an toàn message từ JSON body
        if (message != null && !message.isEmpty()) {
            try {
                JsonNode jsonNode = objectMapper.readTree(message);
                if (jsonNode.has("message")) {
                    message = jsonNode.get("message").asText();
                }
            } catch (Exception e) {
                // Nếu không parse được JSON, giữ nguyên hoặc sử dụng ex.getMessage()
                message = ex.getMessage();
            }
        } else {
            message = ex.getMessage();
        }

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(ex.status() > 0 ? ex.status() : 500)
                .error("Forwarded error from User Service")
                .message(message)
                .path(request.getRequestURI())
                .build();
        
        return new ResponseEntity<>(errorResponse, HttpStatus.valueOf(ex.status() > 0 ? ex.status() : 500));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(RuntimeException ex, HttpServletRequest request) {
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();
        
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleAllExceptions(Exception ex, HttpServletRequest request) {
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();
        
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
