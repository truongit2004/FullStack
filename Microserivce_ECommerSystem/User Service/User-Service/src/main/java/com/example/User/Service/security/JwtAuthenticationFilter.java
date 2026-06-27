package com.example.User.Service.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final StringRedisTemplate redisTemplate;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String username;
        final String role;

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        jwt = authHeader.substring(7);
        try {
            // Redis outage should not block JWT auth flow.
            boolean isBlacklisted = isTokenBlacklisted(jwt);
            if (isBlacklisted) {
                log.debug("Rejected blacklisted token for path {}", request.getRequestURI());
                filterChain.doFilter(request, response);
                return;
            }

            if (jwtService.isTokenValid(jwt)) {
                username = jwtService.extractUsername(jwt);
                role = jwtService.extractRole(jwt);

                if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    // Chuẩn hóa Role: Luôn viết hoa và có tiền tố ROLE_
                    String normalizedRole = role != null ? role.toUpperCase() : "USER";
                    if (!normalizedRole.startsWith("ROLE_")) {
                        normalizedRole = "ROLE_" + normalizedRole;
                    }

                    List<SimpleGrantedAuthority> authorities = Collections.singletonList(
                        new SimpleGrantedAuthority(normalizedRole)
                    );

                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            username,
                            null,
                            authorities
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (Exception e) {
            log.warn("JWT authentication failed for path {}: {}", request.getRequestURI(), e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private boolean isTokenBlacklisted(String jwt) {
        try {
            Boolean blacklisted = redisTemplate.hasKey("blacklist:" + jwt);
            return Boolean.TRUE.equals(blacklisted);
        } catch (Exception redisException) {
            log.warn("Redis blacklist check failed, continue with JWT validation: {}", redisException.getMessage());
            return false;
        }
    }
}
