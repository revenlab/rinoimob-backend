package com.rinoimob.service.rental;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class RentalSecretServiceTest {
    @Test void shouldEncryptWithoutPersistingPlaintext(){RentalSecretService service=new RentalSecretService("01234567890123456789012345678901");String encrypted=service.encrypt("secret-api-key");assertThat(encrypted).doesNotContain("secret-api-key");assertThat(service.decrypt(encrypted)).isEqualTo("secret-api-key");assertThat(service.matchesHash("token",service.sha256("token"))).isTrue();}
}
