package com.example.CartService.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Filter nhận X-User-Id và X-User-Role được inject bởi API Gateway sau khi verify JWT.
 * CartService không cần xác thực token trực tiếp — Gateway đã làm điều đó.
 * Filter này chỉ đọc header và cài SecurityContext để Spring Security biết user là ai.
 */
@Component
public class GatewayHeaderAuthFilter extends OncePerRequestFilter {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLE_HEADER = "X-User-Role";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String userId = request.getHeader(USER_ID_HEADER);
        String role   = request.getHeader(USER_ROLE_HEADER);

        if (userId != null && !userId.isBlank()) {
            String authority = (role != null && !role.isBlank()) ? "ROLE_" + role.toUpperCase() : "ROLE_USER";
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            userId,
                            null,
                            List.of(new SimpleGrantedAuthority(authority))
                    );
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }
}
