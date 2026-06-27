package com.example.API.Gateway.security;

import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class JwtGatewayFilter implements GlobalFilter {

    private final JwtService jwtService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        HttpMethod method = exchange.getRequest().getMethod();

        // 1. Công khai hoàn toàn (Login / Register)
        if (path.contains("/auth/") || 
            (path.equals("/api/users") && method == HttpMethod.POST) ||
            path.contains("/api/auth/")) {
            return chain.filter(exchange);
        }

        // 1.5 Cho phép Khách (Guest) tự do xem Sản phẩm, Danh mục, Lịch sử Review
        if ((path.contains("/api/products") || path.contains("/api/categories") || path.contains("/api/reviews")) 
            && method == HttpMethod.GET) {
            return chain.filter(exchange);
        }

        // 2. Kiểm tra Token
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            System.out.println("DEBUG Gateway: Missing or invalid Authorization header for path: " + path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String jwt = authHeader.substring(7);
        System.out.println("DEBUG Gateway: Received Token (First 10): " + (jwt.length() > 10 ? jwt.substring(0, 10) : jwt) + " for path: " + path);

        try {
            if (jwtService.isTokenValid(jwt)) {
                String userId = jwtService.extractUserId(jwt);
                String username = jwtService.extractUsername(jwt);
                String role = jwtService.extractRole(jwt);

                System.out.println("DEBUG Gateway: Token valid. User: " + username + " (" + userId + "), Role: " + role);

                // --- 3. PHÂN QUYỀN CHO ORDER SERVICE ---
                if (path.contains("/api/orders")) {
                    // Chặn Admin cho các lệnh Get All/Filter
                    if ((path.equals("/api/orders") || path.equals("/api/orders/filter")) && method == HttpMethod.GET) {
                        if (!"ADMIN".equalsIgnoreCase(role)) {
                            System.out.println("DEBUG Gateway: Forbidden for role " + role + " on path " + path);
                            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN); // 403
                            return exchange.getResponse().setComplete();
                        }
                    }
                }

                // --- 4. PHÂN QUYỀN CHO PRODUCT SERVICE ---
                if (path.contains("/api/products") || path.contains("/api/categories")) {
                    // Thêm/Sửa/Xóa sản phẩm -> Phải là ADMIN
                    if (method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.DELETE) {
                        if (!"ADMIN".equalsIgnoreCase(role)) {
                            System.out.println("DEBUG Gateway: Forbidden for role " + role + " on path " + path);
                            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN); // 403
                            return exchange.getResponse().setComplete();
                        }
                    }
                }

                // --- 5. BẢO MẬT CHO CART SERVICE (XÓA GIỎ HÀNG) ---
                if (path.startsWith("/api/cart")) {
                    if (method == HttpMethod.DELETE) {
                        String[] pathParts = path.split("/");
                        // Nếu là xóa cả giỏ theo ID: /api/cart/{userId}
                        // pathParts sẽ là ["", "api", "cart", "{userId}"] -> length = 4
                        if (pathParts.length == 4 && !pathParts[3].equals("items")) {
                            String targetUserId = pathParts[3]; 
                            if (!targetUserId.equals(userId) && !"ADMIN".equalsIgnoreCase(role)) {
                                System.out.println("DEBUG Gateway: Forbidden full cart delete for " + targetUserId + " by " + userId);
                                exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                                return exchange.getResponse().setComplete();
                            }
                        }
                        // Nếu là xóa từng món: /api/cart/items/{productId} -> length = 5
                        // Hoặc xóa nhiều món: /api/cart/items?productIds=... -> length = 4 nhưng có "items"
                        // Những lệnh này Cart Service sẽ tự lấy ID từ Header X-User-Id mà Gateway gửi xuống, nên cực kỳ an toàn.
                    }
                }

                // Gửi thông tin User xuống service con qua Header
                ServerWebExchange mutatedExchange = exchange.mutate()
                        .request(exchange.getRequest().mutate()
                                .header("X-User-Id", userId)
                                .header("X-User-Name", username)
                                .header("X-User-Role", role)
                                .build())
                        .build();
                
                return chain.filter(mutatedExchange);
            } else {
                System.out.println("DEBUG Gateway: isTokenValid returned FALSE for path: " + path);
            }
        } catch (Exception e) {
            System.out.println("DEBUG Gateway: Exception during JWT processing for " + path + ": " + e.getMessage());
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }
}
