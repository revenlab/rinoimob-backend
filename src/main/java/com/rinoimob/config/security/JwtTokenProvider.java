package com.rinoimob.config.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
public class JwtTokenProvider {

    @Value("${jwt.secret:rinoimob-secret-key-change-in-production-minimum-256-bits}")
    private String jwtSecret;

    @Value("${jwt.access-token-expiration:900000}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration:604800000}")
    private long refreshTokenExpiration;

    public String generateAccessToken(UUID userId, String email, String role, UUID tenantId) {
        return generateToken(userId, email, role, tenantId, accessTokenExpiration, "access");
    }

    public String generateRefreshToken(UUID userId, String email) {
        return generateToken(userId, email, null, null, refreshTokenExpiration, "refresh");
    }

    private String generateToken(UUID userId, String email, String role, UUID tenantId, long expiration, String type) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", email);
        claims.put("type", type);
        if (role != null) {
            claims.put("role", role);
        }
        if (tenantId != null) {
            claims.put("tenantId", tenantId.toString());
        }

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(userId.toString())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey(), SignatureAlgorithm.HS512)
                .compact();
    }

    public UUID getUserIdFromToken(String token) {
        Claims claims = getAllClaimsFromToken(token);
        return UUID.fromString(claims.getSubject());
    }

    public String getEmailFromToken(String token) {
        Claims claims = getAllClaimsFromToken(token);
        return claims.get("email", String.class);
    }

    public String getRoleFromToken(String token) {
        Claims claims = getAllClaimsFromToken(token);
        return claims.get("role", String.class);
    }

    public UUID getTenantIdFromToken(String token) {
        try {
            Claims claims = getAllClaimsFromToken(token);
            String tenantId = claims.get("tenantId", String.class);
            return tenantId != null ? UUID.fromString(tenantId) : null;
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isTokenValid(String token) {
        try {
            getAllClaimsFromToken(token);
            return true;
        } catch (Exception e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }

    public boolean isAccessToken(String token) {
        try {
            Claims claims = getAllClaimsFromToken(token);
            return "access".equals(claims.get("type", String.class));
        } catch (Exception e) {
            return false;
        }
    }

    private Claims getAllClaimsFromToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }
}
