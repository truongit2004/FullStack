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
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            // Order Service từ chối có lý do (mã giảm giá hết hạn, không đủ điểm, hết hàng...):
            // chuyển đúng lý do đó cho khách thay vì báo chung chung "không phản hồi"
            log.warn("Order Service từ chối tạo đơn: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new CartException(extractMessage(e));
        } catch (Exception e) {
            log.error("Lỗi khi gọi Order Service: {}", e.getMessage());
            throw new CartException("Order Service không phản hồi. Vui lòng thử lại.");
        }
    }

    // Lấy trường "message" trong JSON lỗi của Order Service
    private String extractMessage(org.springframework.web.client.HttpStatusCodeException e) {
        try {
            com.fasterxml.jackson.databind.JsonNode message =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(e.getResponseBodyAsString()).get("message");
            if (message != null && !message.asText().isBlank()) {
                return message.asText();
            }
        } catch (Exception ignored) {
            // thân phản hồi không phải JSON
        }
        return "Order Service trả về lỗi khi tạo đơn hàng.";
    }
}
