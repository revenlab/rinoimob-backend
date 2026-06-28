package com.rinoimob.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProdConfigValidatorTest {

    @Test
    void validateReturnsErrorsForUnsafeDefaults() {
        ProdConfigValidator validator = new ProdConfigValidator(
                ProdConfigValidator.DEFAULT_JWT_SECRET,
                ProdConfigValidator.DEFAULT_DB_PASSWORD,
                ProdConfigValidator.DEFAULT_EVOLUTION_API_KEY,
                "http://localhost:5173,http://127.0.0.1:3000");

        assertThat(validator.validate())
                .hasSize(4)
                .anyMatch(error -> error.contains("JWT_SECRET"))
                .anyMatch(error -> error.contains("DB_PASSWORD"))
                .anyMatch(error -> error.contains("EVOLUTION_API_KEY"))
                .anyMatch(error -> error.contains("CORS_ALLOWED_ORIGINS"));
    }

    @Test
    void runFailsWhenProdConfigIsUnsafe() {
        ProdConfigValidator validator = new ProdConfigValidator(
                "short",
                "pass",
                "changeme-evolution-apikey",
                "");

        assertThatThrownBy(() -> validator.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsafe prod configuration");
    }

    @Test
    void validatePassesForSafeValues() {
        ProdConfigValidator validator = new ProdConfigValidator(
                "a".repeat(64),
                "db-password-not-default",
                "evolution-key-not-default",
                "https://app.rinoimob.com,https://cliente.rinoimob.com");

        assertThat(validator.validate()).isEmpty();
    }
}
