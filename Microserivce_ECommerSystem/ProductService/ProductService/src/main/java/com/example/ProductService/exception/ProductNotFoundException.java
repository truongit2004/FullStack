package com.example.ProductService.exception;


public class ProductNotFoundException extends ProductException {
    public ProductNotFoundException(String message) {
        super(message);
    }
}