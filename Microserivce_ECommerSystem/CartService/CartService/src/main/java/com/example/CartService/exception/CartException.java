package com.example.CartService.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class CartException extends RuntimeException {
    public CartException(String message) {
        super(message);
    }
}
