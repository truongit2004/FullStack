package com.example.CartService.client;

import com.example.CartService.dto.OrderRequestDTO;
import com.example.CartService.exception.CartException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderClient {

    private final RestTemplate restTemplate;

    @Value("${services.order-url}")
    private String orderServiceUrl;

    /**
     * Tạo order mới, trả về orderId.
     * Forward JWT token sang Order Service để Order Service biết ai gọi.
     */
    public String createOrder(OrderRequestDTO request, String jwtToken) {
        String url = orderServiceUrl + "/api/orders";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(jwtToken);  // forward token

        HttpEntity<OrderRequestDTO> entity = new HttpEntity<>(request, headers);

        try {
            ResponseEntity<Map> response =
                    restTemplate.postForEntity(url, entity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object orderId = response.getBody().get("id");
                return orderId != null ? orderId.toString() : "unknown";
            }
            throw new CartException("Order Service trả về lỗi khi tạo đơn hàng.");

        } catch (CartException e) {
            throw e;
        } catch (Exception e) {
            log.error("Lỗi khi gọi Order Service: {}", e.getMessage());
            throw new CartException("Order Service không phản hồi. Vui lòng thử lại.");
        }
    }
}
