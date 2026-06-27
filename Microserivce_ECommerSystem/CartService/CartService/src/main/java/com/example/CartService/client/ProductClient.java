//package com.example.CartService.client;
//
//import com.example.CartService.dto.ProductResponseDTO;
//import com.example.CartService.exception.CartException;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.http.*;
//import org.springframework.stereotype.Component;
//import org.springframework.web.client.HttpClientErrorException;
//import org.springframework.web.client.RestTemplate;
//
//@Slf4j
//@Component
//@RequiredArgsConstructor
//public class ProductClient {
//
//    private final RestTemplate restTemplate;
//
//    @Value("${services.product-url}")
//    private String productServiceUrl;
//
//    /**
//     * Lấy thông tin product theo id.
//     * Ném CartException nếu không tìm thấy hoặc product đã bị xóa / inactive.
//     */
//    public ProductResponseDTO getProductById(String productId) {
//        String url = productServiceUrl + "/api/products/" + productId;
//        try {
//            ResponseEntity<ProductResponseDTO> response =
//                    restTemplate.getForEntity(url, ProductResponseDTO.class);
//
//            ProductResponseDTO product = response.getBody();
//            if (product == null) {
//                throw new CartException("Không tìm thấy sản phẩm: " + productId);
//            }
//            if (Boolean.TRUE.equals(product.getDeleted())) {
//                throw new CartException("Sản phẩm đã bị xóa: " + productId);
//            }
//            if (!"ACTIVE".equalsIgnoreCase(product.getProductStatus())) {
//                throw new CartException("Sản phẩm không còn kinh doanh: " + product.getName());
//            }
//            return product;
//
//        } catch (HttpClientErrorException.NotFound e) {
//            throw new CartException("Không tìm thấy sản phẩm: " + productId);
//        } catch (CartException e) {
//            throw e;
//        } catch (Exception e) {
//            log.error("Lỗi khi gọi Product Service: {}", e.getMessage());
//            throw new CartException("Product Service không phản hồi. Vui lòng thử lại.");
//        }
//    }
//
//    /**
//     * Giảm stock sau khi checkout thành công.
//     */
//    public void decreaseStock(String productId, Integer quantity) {
//        String url = productServiceUrl + "/api/products/" + productId
//                + "/stock/decrease?quantity=" + quantity;
//        try {
//            restTemplate.put(url, null);
//        } catch (Exception e) {
//            log.error("Không thể giảm stock product {}: {}", productId, e.getMessage());
//            // Không throw — rollback stock nên được xử lý riêng (saga / compensation)
//        }
//    }
//
//    /**
//     * Hoàn lại stock nếu checkout thất bại.
//     */
//    public void increaseStock(String productId, Integer quantity) {
//        String url = productServiceUrl + "/api/products/" + productId
//                + "/stock/increase?quantity=" + quantity;
//        try {
//            restTemplate.put(url, null);
//        } catch (Exception e) {
//            log.error("Không thể hoàn stock product {}: {}", productId, e.getMessage());
//        }
//    }
//}
package com.example.CartService.client;

import com.example.CartService.dto.ProductResponseDTO;
import com.example.CartService.exception.CartException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductClient {

    private final RestTemplate restTemplate;

    @Value("${services.product-url}")
    private String productServiceUrl;

    // ── Lấy token từ request hiện tại ────────────────────────────
    private HttpEntity<?> buildAuthHeader() {
        HttpHeaders headers = new HttpHeaders();
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                String token = (String) attrs.getRequest().getAttribute("jwt-token");
                if (token != null) {
                    headers.setBearerAuth(token); // forward token sang Product Service
                }
            }
        } catch (Exception e) {
            log.warn("Không lấy được token từ request: {}", e.getMessage());
        }
        return new HttpEntity<>(headers);
    }

    public ProductResponseDTO getProductById(String productId) {
        String url = productServiceUrl + "/api/products/" + productId;
        try {
            ResponseEntity<ProductResponseDTO> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    buildAuthHeader(), // ← forward token
                    ProductResponseDTO.class
            );

            ProductResponseDTO product = response.getBody();
            if (product == null) {
                throw new CartException("Không tìm thấy sản phẩm: " + productId);
            }
            if (Boolean.TRUE.equals(product.getDeleted())) {
                throw new CartException("Sản phẩm đã bị xóa: " + productId);
            }
            if (!"ACTIVE".equalsIgnoreCase(product.getProductStatus())) {
                throw new CartException("Sản phẩm không còn kinh doanh: " + product.getName());
            }
            return product;

        } catch (HttpClientErrorException.NotFound e) {
            throw new CartException("Không tìm thấy sản phẩm: " + productId);
        } catch (CartException e) {
            throw e;
        } catch (Exception e) {
            log.error("Lỗi khi gọi Product Service: {}", e.getMessage());
            throw new CartException("Product Service không phản hồi. Vui lòng thử lại.");
        }
    }

    public void decreaseStock(String productId, Integer quantity) {
        String url = productServiceUrl + "/api/products/" + productId
                + "/stock/decrease?quantity=" + quantity;
        try {
            restTemplate.exchange(
                    url,
                    HttpMethod.PUT,
                    buildAuthHeader(), // ← forward token
                    Void.class
            );
        } catch (Exception e) {
            log.error("Không thể giảm stock product {}: {}", productId, e.getMessage());
            throw new RuntimeException("Lỗi khi kết nối Product Service để cập nhật kho.");
        }
    }

    public void increaseStock(String productId, Integer quantity) {
        String url = productServiceUrl + "/api/products/" + productId
                + "/stock/increase?quantity=" + quantity;
        try {
            restTemplate.exchange(
                    url,
                    HttpMethod.PUT,
                    buildAuthHeader(), // ← forward token
                    Void.class
            );
        } catch (Exception e) {
            log.error("Không thể hoàn stock product {}: {}", productId, e.getMessage());
            // Rollback thất bại thì log lỗi, ko làm gì thêm để ko quăng Exception ra ngoài gây lỗi kép
        }
    }
}