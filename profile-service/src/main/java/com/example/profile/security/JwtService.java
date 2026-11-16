package com.example.profile.security;

import io.jsonwebtoken.Claims;
import jakarta.annotation.PostConstruct;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtService {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration-seconds:900}")
    private long expirationSeconds;

    @Value("${app.jwt.issuer:student-profile-auth-service}")
    private String issuer;

    @PostConstruct
    void validateConfiguration() {
        if (jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET chưa được cấu hình hoặc ngắn hơn 32 byte. Hãy chạy scripts/configure-local-secrets.ps1.");
        }
        if (expirationSeconds <= 0) {
            throw new IllegalStateException("JWT_EXPIRATION_SECONDS phải lớn hơn 0.");
        }
    }

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String username, String role, String studentId) {
        return buildToken(username, role, studentId, expirationSeconds * 1000L);
    }

    // Dùng để tạo token đã hết hạn phục vụ kiểm thử Giai đoạn 2 Bước 4 & Giai đoạn 5 của TH5
    public String generateExpiredToken(String username, String role, String studentId) {
        return buildToken(username, role, studentId, -60000L);
    }

    private String buildToken(String username, String role, String studentId, long ttlMillis) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role);
        if (studentId != null) {
            claims.put("studentId", studentId);
        }
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(username)
                .setIssuer(issuer)
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + ttlMillis))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims parseAndValidateToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .requireIssuer(issuer)
                .build()
                .parseClaimsJws(token)
                .getBody();
        return claims;
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }
}
