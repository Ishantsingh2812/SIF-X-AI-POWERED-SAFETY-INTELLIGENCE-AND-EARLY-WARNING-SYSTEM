package com.sih.sif.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JwtTokenProvider.java
 *
 * Generates and validates signed JWT tokens using HMAC-SHA keys.
 */
@Component
public class JwtTokenProvider {

    @Value("${app.jwt.secret:sif_enterprise_default_secret_key_change_in_production_32chars}")
    private String jwtSecret;

    @Value("${app.jwt.expiry-minutes:1440}")
    private long expiryMinutes;

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    private SecretKey key;

    @PostConstruct
    public void init() {
        if ("prod".equalsIgnoreCase(activeProfile) || "production".equalsIgnoreCase(activeProfile)) {
            if (jwtSecret == null || jwtSecret.length() < 32 || jwtSecret.contains("change_in_production")) {
                throw new IllegalStateException("JWT_SECRET must be configured with at least 32 characters in production!");
            }
        }
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            // Pad key bytes for local dev fallback to satisfy HMAC-SHA-256 requirement
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
            keyBytes = padded;
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(String username, String role) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + (expiryMinutes * 60 * 1000));

        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    public String getUsernameFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.getSubject();
    }

    public String getRoleFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.get("role", String.class);
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
}
