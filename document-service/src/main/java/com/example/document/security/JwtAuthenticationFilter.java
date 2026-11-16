package com.example.document.security;

import io.jsonwebtoken.Claims;
import jakarta.annotation.PostConstruct;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.issuer:student-profile-auth-service}")
    private String issuer;

    @PostConstruct
    void validateJwtConfiguration() {
        if (jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET chưa được cấu hình hoặc ngắn hơn 32 byte. Hãy chạy scripts/configure-local-secrets.ps1.");
        }
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalStateException("JWT_ISSUER không được để trống.");
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                Key signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
                Claims claims = Jwts.parserBuilder()
                        .setSigningKey(signingKey)
                        .requireIssuer(issuer)
                        .build()
                        .parseClaimsJws(token)
                        .getBody();

                String username = claims.getSubject();
                String role = claims.get("role", String.class);
                String studentId = claims.get("studentId", String.class);

                if (username == null || username.isBlank() || role == null ||
                        !List.of("STUDENT", "STAFF", "ADMIN").contains(role.toUpperCase(java.util.Locale.ROOT))) {
                    throw new IllegalArgumentException("JWT thiếu subject hoặc chứa vai trò không được hỗ trợ");
                }

                AuthenticatedUser principal = new AuthenticatedUser(username, role.toUpperCase(java.util.Locale.ROOT), studentId);
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))
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
