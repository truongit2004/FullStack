package com.example.OrderService.client;

import com.example.OrderService.dto.ProductResponseDTO;
import com.example.OrderService.exception.AppException;
import com.example.OrderService.exception.ErrorCode;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
//@FeignClient(
//        name = "product-service",
//        url = "${product.service.url}",
//        fallback = ProductClientFallback.class   // ✅ thêm dòng này
//)
@Component
public class ProductClientFallback implements ProductClient {

    @Override
    public ProductResponseDTO getProductById(String productId) {
        throw new AppException(ErrorCode.PRODUCT_SERVICE_UNAVAILABLE);
    }

    @Override
    public void decreaseStock(String productId, Integer quantity) {
        throw new AppException(ErrorCode.PRODUCT_SERVICE_UNAVAILABLE);
    }

    @Override
    public void increaseStock(String productId, Integer quantity) {
        throw new AppException(ErrorCode.PRODUCT_SERVICE_UNAVAILABLE);
    }
}