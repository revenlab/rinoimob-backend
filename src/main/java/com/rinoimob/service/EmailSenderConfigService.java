package com.rinoimob.service;

import com.rinoimob.context.TenantContext;
import com.rinoimob.domain.dto.CreateEmailSenderConfigRequest;
import com.rinoimob.domain.dto.EmailSenderConfigResponse;
import com.rinoimob.domain.dto.UpdateEmailSenderConfigRequest;
import com.rinoimob.domain.entity.EmailSenderConfig;
import com.rinoimob.domain.repository.EmailSenderConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailSenderConfigService {

    private final EmailSenderConfigRepository repository;

    public List<EmailSenderConfigResponse> listForTenant() {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        return repository.findByTenantIdOrderByCreatedAtAsc(tenantId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public EmailSenderConfigResponse getById(UUID id) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        EmailSenderConfig config = repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Email sender config not found"));
        return toResponse(config);
    }

    @Transactional
    public EmailSenderConfigResponse create(CreateEmailSenderConfigRequest req) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());

        if (Boolean.TRUE.equals(req.getIsDefault())) {
            repository.clearDefaultForTenant(tenantId);
        }

        EmailSenderConfig config = new EmailSenderConfig();
        config.setTenantId(tenantId);
        config.setDisplayName(req.getDisplayName());
        config.setFromEmail(req.getFromEmail());
        config.setFromName(req.getFromName());
        config.setSmtpHost(req.getSmtpHost());
        config.setSmtpPort(req.getSmtpPort() != null ? req.getSmtpPort() : 587);
        config.setSmtpUsername(req.getSmtpUsername());
        config.setSmtpPassword(req.getSmtpPassword());
        config.setSmtpTls(req.getSmtpTls() != null ? req.getSmtpTls() : true);
        config.setIsDefault(req.getIsDefault() != null ? req.getIsDefault() : false);

        return toResponse(repository.save(config));
    }

    @Transactional
    public EmailSenderConfigResponse update(UUID id, UpdateEmailSenderConfigRequest req) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        EmailSenderConfig config = repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Email sender config not found"));

        if (Boolean.TRUE.equals(req.getIsDefault()) && !Boolean.TRUE.equals(config.getIsDefault())) {
            repository.clearDefaultForTenant(tenantId);
        }

        config.setDisplayName(req.getDisplayName());
        config.setFromEmail(req.getFromEmail());
        config.setFromName(req.getFromName());
        config.setSmtpHost(req.getSmtpHost());
        config.setSmtpPort(req.getSmtpPort());
        config.setSmtpUsername(req.getSmtpUsername());
        if (req.getSmtpPassword() != null && !req.getSmtpPassword().isBlank()) {
            config.setSmtpPassword(req.getSmtpPassword());
        }
        if (req.getSmtpTls() != null) {
            config.setSmtpTls(req.getSmtpTls());
        }
        if (req.getIsDefault() != null) {
            config.setIsDefault(req.getIsDefault());
        }

        return toResponse(repository.save(config));
    }

    @Transactional
    public void delete(UUID id) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        EmailSenderConfig config = repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Email sender config not found"));
        repository.delete(config);
    }

    /** Resolves the config by ID scoped to the given tenant (used internally by action handler). */
    public EmailSenderConfig resolveForTenant(UUID configId, UUID tenantId) {
        return repository.findByIdAndTenantId(configId, tenantId)
                .orElseThrow(() -> new RuntimeException("Email sender config not found: " + configId));
    }

    public EmailSenderConfigResponse toResponse(EmailSenderConfig c) {
        return new EmailSenderConfigResponse(
                c.getId(),
                c.getDisplayName(),
                c.getFromEmail(),
                c.getFromName(),
                c.getSmtpHost(),
                c.getSmtpPort(),
                c.getSmtpUsername(),
                c.getSmtpTls(),
                c.getIsDefault(),
                c.getCreatedAt(),
                c.getUpdatedAt()
        );
    }
}
