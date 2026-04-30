package com.rinoimob.config.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@Slf4j
public class JwtTokenProvider {

    @Value("${jwt.secret:rinoimob-dev-secret-key-change-in-production-must-be-at-least-512-bits-long!!}")
    private String jwtSecret;

    @Value("${jwt.access-token-expiration:900000}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration:604800000}")
    private long refreshTokenExpiration;

    public String generateAccessToken(UUID userId, String email, String role, UUID tenantId) {
        return generateAccessToken(userId, email, role, tenantId, List.of());
    }

    public String generateAccessToken(UUID userId, String email, String role, UUID tenantId, List<String> permissions) {
        String jti = UUID.randomUUID().toString();
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", email);
        claims.put("type", "access");
        claims.put("jti", jti);
        if (role != null) {
            claims.put("role", role);
        }
        if (tenantId != null) {
            claims.put("tenantId", tenantId.toString());
        }
        if (permissions != null && !permissions.isEmpty()) {
            claims.put("permissions", String.join(",", permissions));
        }

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(userId.toString())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + accessTokenExpiration))
                .signWith(getSigningKey(), SignatureAlgorithm.HS512)
                .compact();
    }

    public String generateRefreshToken(UUID userId, String email) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", email);
        claims.put("type", "refresh");

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(userId.toString())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + refreshTokenExpiration))
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

    public String getJtiFromToken(String token) {
        try {
            Claims claims = getAllClaimsFromToken(token);
            return claims.get("jti", String.class);
        } catch (Exception e) {
            return null;
        }
    }

    public List<String> getPermissionsFromToken(String token) {
        try {
            Claims claims = getAllClaimsFromToken(token);
            String perms = claims.get("permissions", String.class);
            if (perms == null || perms.isBlank()) return List.of();
            return Arrays.stream(perms.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return List.of();
        }
    }

    public long getAccessTokenTtlSeconds() {
        return accessTokenExpiration / 1000;
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

    public String generatePreAuthToken(String email, List<UUID> allowedTenantIds) {
        String tenants = allowedTenantIds.stream()
                .map(UUID::toString)
                .collect(Collectors.joining(","));
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", email);
        claims.put("type", "pre_auth");
        claims.put("allowedTenants", tenants);
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(email)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 300_000L))
                .signWith(getSigningKey(), SignatureAlgorithm.HS512)
                .compact();
    }

    public String getEmailFromPreAuthToken(String token) {
        return getAllClaimsFromToken(token).getSubject();
    }

    public List<UUID> getAllowedTenantsFromPreAuthToken(String token) {
        String tenants = getAllClaimsFromToken(token).get("allowedTenants", String.class);
        if (tenants == null || tenants.isBlank()) return List.of();
        return Arrays.stream(tenants.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(UUID::fromString)
                .collect(Collectors.toList());
    }

    public boolean isPreAuthToken(String token) {
        try {
            Claims claims = getAllClaimsFromToken(token);
            return "pre_auth".equals(claims.get("type", String.class));
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
