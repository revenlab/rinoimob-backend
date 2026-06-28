package com.rinoimob.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;

@Component
@Profile("prod")
public class ProdConfigValidator implements ApplicationRunner {

    static final String DEFAULT_JWT_SECRET = "rinoimob-dev-secret-key-change-in-production-must-be-at-least-512-bits-long!!";
    static final String DEFAULT_DB_PASSWORD = "pass";
    static final String DEFAULT_EVOLUTION_API_KEY = "changeme-evolution-apikey";

    private final String jwtSecret;
    private final String dbPassword;
    private final String evolutionApiKey;
    private final String corsAllowedOrigins;

    public ProdConfigValidator(
            @Value("${jwt.secret:}") String jwtSecret,
            @Value("${spring.datasource.password:}") String dbPassword,
            @Value("${evolution.api.key:}") String evolutionApiKey,
            @Value("${app.cors.allowed-origins:}") String corsAllowedOrigins) {
        this.jwtSecret = jwtSecret;
        this.dbPassword = dbPassword;
        this.evolutionApiKey = evolutionApiKey;
        this.corsAllowedOrigins = corsAllowedOrigins;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> errors = validate();
        if (!errors.isEmpty()) {
            throw new IllegalStateException("Unsafe prod configuration: " + String.join("; ", errors));
        }
    }

    List<String> validate() {
        List<String> errors = new java.util.ArrayList<>();
        if (!StringUtils.hasText(jwtSecret)
                || DEFAULT_JWT_SECRET.equals(jwtSecret)
                || jwtSecret.length() < 64) {
            errors.add("JWT_SECRET must be set to a strong non-default value with at least 64 characters");
        }
        if (DEFAULT_DB_PASSWORD.equals(dbPassword)) {
            errors.add("DB_PASSWORD must not use the default 'pass'");
        }
        if (DEFAULT_EVOLUTION_API_KEY.equals(evolutionApiKey)) {
            errors.add("EVOLUTION_API_KEY must not use the default value");
        }
        if (!StringUtils.hasText(corsAllowedOrigins) || onlyLocalhostOrigins(corsAllowedOrigins)) {
            errors.add("CORS_ALLOWED_ORIGINS must include production origins and not only localhost");
        }
        return errors;
    }

    private boolean onlyLocalhostOrigins(String allowedOrigins) {
        return Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .allMatch(this::isLocalhostOrigin);
    }

    private boolean isLocalhostOrigin(String origin) {
        String lower = origin.toLowerCase();
        return lower.contains("localhost")
                || lower.contains("127.0.0.1")
                || lower.contains("0.0.0.0");
    }
}
