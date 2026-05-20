package com.rinoimob.service;

import com.rinoimob.context.TenantContext;
import com.rinoimob.domain.dto.CreateEmailSenderConfigRequest;
import com.rinoimob.domain.dto.EmailSenderConfigResponse;
import com.rinoimob.domain.dto.UpdateEmailSenderConfigRequest;
import com.rinoimob.domain.entity.EmailSenderConfig;
import com.rinoimob.domain.repository.EmailSenderConfigRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailSenderConfigServiceTest {

    @Mock
    private EmailSenderConfigRepository repository;

    @InjectMocks
    private EmailSenderConfigService service;

    private static final String TENANT_ID_STR = "11111111-1111-1111-1111-111111111111";
    private static final UUID TENANT_ID = UUID.fromString(TENANT_ID_STR);
    private static final UUID CONFIG_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID_STR);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void shouldListConfigsForCurrentTenant() {
        EmailSenderConfig config = buildConfig(CONFIG_ID, false);
        when(repository.findByTenantIdOrderByCreatedAtAsc(TENANT_ID)).thenReturn(List.of(config));

        List<EmailSenderConfigResponse> result = service.listForTenant();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(CONFIG_ID);
        assertThat(result.get(0).getDisplayName()).isEqualTo("Work Email");
    }

    @Test
    void shouldCreateConfigWithDefaultFalseWhenNotSet() {
        CreateEmailSenderConfigRequest req = new CreateEmailSenderConfigRequest();
        req.setDisplayName("Sales SMTP");
        req.setFromEmail("sales@example.com");
        req.setSmtpHost("smtp.example.com");
        req.setSmtpPort(587);
        req.setSmtpTls(true);
        req.setIsDefault(false);

        EmailSenderConfig saved = buildConfig(CONFIG_ID, false);
        when(repository.save(any())).thenReturn(saved);

        EmailSenderConfigResponse response = service.create(req);

        assertThat(response.getId()).isEqualTo(CONFIG_ID);
        verify(repository, never()).clearDefaultForTenant(any());
    }

    @Test
    void shouldClearOtherDefaultsWhenCreatingDefaultConfig() {
        CreateEmailSenderConfigRequest req = new CreateEmailSenderConfigRequest();
        req.setDisplayName("Default SMTP");
        req.setFromEmail("default@example.com");
        req.setSmtpHost("smtp.example.com");
        req.setSmtpPort(587);
        req.setIsDefault(true);

        EmailSenderConfig saved = buildConfig(CONFIG_ID, true);
        when(repository.save(any())).thenReturn(saved);

        service.create(req);

        verify(repository).clearDefaultForTenant(TENANT_ID);
    }

    @Test
    void shouldUpdateConfigAndKeepExistingPasswordWhenPasswordNotProvided() {
        EmailSenderConfig existing = buildConfig(CONFIG_ID, false);
        existing.setSmtpPassword("existingSecret");
        when(repository.findByIdAndTenantId(CONFIG_ID, TENANT_ID)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateEmailSenderConfigRequest req = new UpdateEmailSenderConfigRequest();
        req.setDisplayName("Updated Name");
        req.setFromEmail("updated@example.com");
        req.setSmtpHost("smtp.example.com");
        req.setSmtpPort(587);
        req.setSmtpPassword(null); // not updating password

        EmailSenderConfigResponse result = service.update(CONFIG_ID, req);

        assertThat(result.getDisplayName()).isEqualTo("Updated Name");
        assertThat(existing.getSmtpPassword()).isEqualTo("existingSecret");
    }

    @Test
    void shouldUpdatePasswordWhenNewPasswordProvided() {
        EmailSenderConfig existing = buildConfig(CONFIG_ID, false);
        existing.setSmtpPassword("oldSecret");
        when(repository.findByIdAndTenantId(CONFIG_ID, TENANT_ID)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateEmailSenderConfigRequest req = new UpdateEmailSenderConfigRequest();
        req.setDisplayName("Name");
        req.setFromEmail("a@example.com");
        req.setSmtpHost("smtp.example.com");
        req.setSmtpPort(465);
        req.setSmtpPassword("newSecret");

        service.update(CONFIG_ID, req);

        assertThat(existing.getSmtpPassword()).isEqualTo("newSecret");
    }

    @Test
    void shouldDeleteConfigWhenFoundForTenant() {
        EmailSenderConfig config = buildConfig(CONFIG_ID, false);
        when(repository.findByIdAndTenantId(CONFIG_ID, TENANT_ID)).thenReturn(Optional.of(config));

        service.delete(CONFIG_ID);

        verify(repository).delete(config);
    }

    @Test
    void shouldThrowWhenConfigNotFoundOnDelete() {
        when(repository.findByIdAndTenantId(CONFIG_ID, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(CONFIG_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void shouldThrowWhenConfigNotFoundOnResolveForTenant() {
        when(repository.findByIdAndTenantId(CONFIG_ID, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveForTenant(CONFIG_ID, TENANT_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("not found");
    }

    private EmailSenderConfig buildConfig(UUID id, boolean isDefault) {
        EmailSenderConfig c = new EmailSenderConfig();
        c.setId(id);
        c.setTenantId(TENANT_ID);
        c.setDisplayName("Work Email");
        c.setFromEmail("work@example.com");
        c.setSmtpHost("smtp.example.com");
        c.setSmtpPort(587);
        c.setSmtpTls(true);
        c.setIsDefault(isDefault);
        return c;
    }
}
