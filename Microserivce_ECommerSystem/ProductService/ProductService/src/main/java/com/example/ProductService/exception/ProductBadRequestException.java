package com.example.ProductService.exception;



public class ProductBadRequestException extends ProductException {
    public ProductBadRequestException(String message) {
        super(message);
    }
}