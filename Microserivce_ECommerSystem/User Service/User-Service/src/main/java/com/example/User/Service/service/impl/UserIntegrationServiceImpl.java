package com.example.User.Service.service.impl;

import com.example.User.Service.dto.UserDto;
import com.example.User.Service.dto.UserProfileResponse;
import com.example.User.Service.dto.external.OrderItemResponseDTO;
import com.example.User.Service.dto.external.OrderResponseDTO;
import com.example.User.Service.dto.external.PageResponse;
import com.example.User.Service.dto.external.ProductResponseDTO;
import com.example.User.Service.service.UserIntegrationService;
import com.example.User.Service.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserIntegrationServiceImpl implements UserIntegrationService {

    private final UserService userService;
    private final RestTemplate restTemplate;

    // Hardcoded URLs based on the user's infrastructure
    private static final String ORDER_SERVICE_URL = "http://localhost:8084/api/orders";
    private static final String PRODUCT_SERVICE_URL = "http://localhost:8082/api/products";

    @Override
    public UserProfileResponse getFullUserProfile(Long userId) {
        // 1. Fetch User Info
        UserDto userInfo = userService.getUserById(userId);

        List<OrderResponseDTO> orders = new ArrayList<>();

        // 2. Fetch Orders from Order Service
        try {
            String url = ORDER_SERVICE_URL + "/user/" + userId + "?page=0&size=100";
            ResponseEntity<PageResponse<OrderResponseDTO>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<PageResponse<OrderResponseDTO>>() {}
            );

            if (response.getBody() != null && response.getBody().getContent() != null) {
                orders = response.getBody().getContent();

                // 3. For each Order, fetch rich Product details for each Item
                for (OrderResponseDTO order : orders) {
                    if (order.getItems() != null) {
                        for (OrderItemResponseDTO item : order.getItems()) {
                            try {
                                ProductResponseDTO productDetails = restTemplate.getForObject(
                                        PRODUCT_SERVICE_URL + "/" + item.getProductId(),
                                        ProductResponseDTO.class
                                );
                                item.setProductDetails(productDetails);
                            } catch (Exception e) {
                                log.warn("Không thể lấy thông tin Product ID: {}. Lỗi: {}", item.getProductId(), e.getMessage());
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Lỗi khi kết nối sang Order Service cho User {}: {}", userId, e.getMessage());
            // It's safe to continue. We just return a profile with empty order history.
        }

        // 4. Aggregate and return
        return UserProfileResponse.builder()
                .userInfo(userInfo)
                .orderHistory(orders)
                .build();
    }
}
