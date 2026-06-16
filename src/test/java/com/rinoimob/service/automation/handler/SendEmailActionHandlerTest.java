package com.rinoimob.service.automation.handler;

import com.rinoimob.context.TenantContext;
import com.rinoimob.domain.entity.EmailSenderConfig;
import com.rinoimob.domain.entity.Lead;
import com.rinoimob.domain.repository.LeadRepository;
import com.rinoimob.domain.repository.UserRepository;
import com.rinoimob.service.core.EmailSenderConfigService;
import com.rinoimob.service.email.EmailService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SendEmailActionHandlerTest {

    @Mock
    private EmailService emailService;

    @Mock
    private EmailSenderConfigService emailSenderConfigService;

    @Mock
    private LeadRepository leadRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private Environment environment;

    @InjectMocks
    private SendEmailActionHandler handler;

    private static final String TENANT_ID_STR = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final UUID CONFIG_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID_STR);
        lenient().when(environment.getActiveProfiles()).thenReturn(new String[] {"dev"});
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void shouldSendEmailUsingDefaultServiceWhenNoSenderConfigIdProvided() throws Exception {
        Map<String, Object> actionData = new HashMap<>();
        actionData.put("recipientType", "CUSTOM_EMAIL");
        actionData.put("recipientEmail", "recipient@test.com");
        actionData.put("subject", "Hello");
        actionData.put("body", "World");

        Map<String, Object> context = new HashMap<>();
        Map<String, Object> result = new HashMap<>();

        handler.execute(actionData, context, result);

        verify(emailService).sendEmail("recipient@test.com", "Hello", "World");
        verify(emailService, never()).sendEmailWithConfig(any(), any(), any(), any());
        assertThat(result.get("email_sent")).isEqualTo(true);
    }

    @Test
    void shouldRejectWorkflowEmailWithoutSenderConfigOutsideDev() throws Exception {
        when(environment.getActiveProfiles()).thenReturn(new String[] {"prod"});

        Map<String, Object> actionData = new HashMap<>();
        actionData.put("recipientType", "CUSTOM_EMAIL");
        actionData.put("recipientEmail", "recipient@test.com");
        actionData.put("subject", "Hello");
        actionData.put("body", "World");

        Map<String, Object> context = new HashMap<>();
        Map<String, Object> result = new HashMap<>();

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> handler.execute(actionData, context, result));

        verify(emailService, never()).sendEmail(any(), any(), any());
        verify(emailService, never()).sendEmailWithConfig(any(), any(), any(), any());
        assertThat(result.get("email_sent")).isEqualTo(false);
    }

    @Test
    void shouldSendEmailWithConfigWhenSenderConfigIdProvided() throws Exception {
        EmailSenderConfig config = new EmailSenderConfig();
        config.setId(CONFIG_ID);
        config.setDisplayName("Custom SMTP");

        when(emailSenderConfigService.resolveForTenant(eq(CONFIG_ID), any())).thenReturn(config);

        Map<String, Object> actionData = new HashMap<>();
        actionData.put("recipientType", "CUSTOM_EMAIL");
        actionData.put("recipientEmail", "client@test.com");
        actionData.put("subject", "Test Subject");
        actionData.put("body", "Test Body");
        actionData.put("senderConfigId", CONFIG_ID.toString());

        Map<String, Object> context = new HashMap<>();
        Map<String, Object> result = new HashMap<>();

        handler.execute(actionData, context, result);

        verify(emailService, never()).sendEmail(any(), any(), any());
        verify(emailService).sendEmailWithConfig(eq(config), eq("client@test.com"), eq("Test Subject"), eq("Test Body"));
        assertThat(result.get("email_sent")).isEqualTo(true);
    }

    @Test
    void shouldFallBackToDefaultServiceWhenSenderConfigNotFound() throws Exception {
        when(emailSenderConfigService.resolveForTenant(any(), any()))
                .thenThrow(new RuntimeException("Config not found"));

        Map<String, Object> actionData = new HashMap<>();
        actionData.put("recipientType", "CUSTOM_EMAIL");
        actionData.put("recipientEmail", "fallback@test.com");
        actionData.put("subject", "Fallback");
        actionData.put("body", "Body");
        actionData.put("senderConfigId", CONFIG_ID.toString());

        Map<String, Object> context = new HashMap<>();
        Map<String, Object> result = new HashMap<>();

        handler.execute(actionData, context, result);

        verify(emailService).sendEmail("fallback@test.com", "Fallback", "Body");
        assertThat(result.get("email_sent")).isEqualTo(true);
    }

    @Test
    void shouldReturnErrorWhenRecipientEmailMissing() throws Exception {
        Map<String, Object> actionData = new HashMap<>();
        actionData.put("recipientType", "CUSTOM_EMAIL");
        actionData.put("subject", "Subject");
        actionData.put("body", "Body");

        Map<String, Object> context = new HashMap<>();
        Map<String, Object> result = new HashMap<>();

        handler.execute(actionData, context, result);

        assertThat(result.get("email_sent")).isEqualTo(false);
        assertThat(result.get("email_error")).asString().contains("email");
        verify(emailService, never()).sendEmail(any(), any(), any());
    }

    @Test
    void shouldResolveLeadEmailFromContextWhenRecipientTypeIsLead() throws Exception {
        UUID leadId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        UUID tenantId = UUID.fromString(TENANT_ID_STR);

        Lead lead = new Lead();
        lead.setEmail("lead@test.com");

        when(leadRepository.findByIdAndTenantIdAndDeletedAtIsNull(leadId, tenantId))
                .thenReturn(Optional.of(lead));

        Map<String, Object> actionData = new HashMap<>();
        actionData.put("recipientType", "LEAD");
        actionData.put("subject", "Lead email");
        actionData.put("body", "Hello lead");

        Map<String, Object> context = new HashMap<>();
        context.put("leadId", leadId.toString());

        Map<String, Object> result = new HashMap<>();

        handler.execute(actionData, context, result);

        verify(emailService).sendEmail("lead@test.com", "Lead email", "Hello lead");
        assertThat(result.get("email_sent")).isEqualTo(true);
    }
}
