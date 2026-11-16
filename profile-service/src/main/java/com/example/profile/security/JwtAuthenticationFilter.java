package com.example.profile.security;

import io.jsonwebtoken.Claims;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtService jwtService;

    @Value("${app.internal.service-key}")
    private String internalServiceKey;

    @PostConstruct
    void validateInternalServiceKey() {
        if (internalServiceKey == null || internalServiceKey.isBlank()) {
            throw new IllegalStateException("INTERNAL_SERVICE_KEY chưa được cấu hình. Hãy chạy scripts/configure-local-secrets.ps1.");
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 1. Kiểm tra xác thực liên dịch vụ (Service-to-Service Security - TH5 Giai đoạn 5 Bước 3)
        String serviceKeyHeader = request.getHeader("X-Internal-Service-Key");
        if (serviceKeyHeader != null && internalServiceKey != null && !internalServiceKey.isBlank() &&
                MessageDigest.isEqual(serviceKeyHeader.getBytes(StandardCharsets.UTF_8),
                        internalServiceKey.getBytes(StandardCharsets.UTF_8))) {
            AuthenticatedUser internalPrincipal = new AuthenticatedUser("document-service", "INTERNAL_SERVICE", null);
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    internalPrincipal, null, List.of(new SimpleGrantedAuthority("ROLE_INTERNAL_SERVICE")));
            SecurityContextHolder.getContext().setAuthentication(auth);
            filterChain.doFilter(request, response);
            return;
        }

        // 2. Kiểm tra Bearer JWT của người dùng
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                Claims claims = jwtService.parseAndValidateToken(token);
                String username = claims.getSubject();
                String role = claims.get("role", String.class);
                String studentId = claims.get("studentId", String.class);

                if (username == null || username.isBlank() || role == null ||
                        !List.of("STUDENT", "STAFF", "ADMIN").contains(role.toUpperCase(java.util.Locale.ROOT))) {
                    throw new IllegalArgumentException("JWT thiếu subject hoặc chứa vai trò không được hỗ trợ");
                }

                String normalizedRole = role.toUpperCase(java.util.Locale.ROOT);
                AuthenticatedUser principal = new AuthenticatedUser(username, normalizedRole, studentId);
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + normalizedRole))
                );
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception ex) {
                SecurityContextHolder.clearContext();
                request.setAttribute("jwt_error", "Token không hợp lệ hoặc đã hết hạn.");
            }
        }

        filterChain.doFilter(request, response);
    }
}
