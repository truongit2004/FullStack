package com.example.CartService.client;

import com.example.CartService.dto.InventoryResponseDTO;
import com.example.CartService.exception.CartException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryClient {

    private final RestTemplate restTemplate;

    @Value("${services.inventory-url:http://inventory-service}")
    private String inventoryServiceUrl;

    public InventoryResponseDTO getStock(String productId) {
        String url = inventoryServiceUrl + "/api/inventory/" + productId;

        try {
            return restTemplate.getForObject(url, InventoryResponseDTO.class);
        } catch (Exception e) {
            log.error("Error calling Inventory Service: {}", e.getMessage());
            throw new CartException("Không thể kiểm tra tồn kho. Vui lòng thử lại.");
        }
    }
}
