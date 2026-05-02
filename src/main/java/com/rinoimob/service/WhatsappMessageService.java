package com.rinoimob.service;

import com.rinoimob.api.client.EvolutionApiClient;
import com.rinoimob.context.TenantContext;
import com.rinoimob.domain.dto.SendWhatsappMessageRequest;
import com.rinoimob.domain.dto.WhatsappMessageResponse;
import com.rinoimob.domain.entity.Lead;
import com.rinoimob.domain.entity.WhatsappInstance;
import com.rinoimob.domain.entity.WhatsappMessage;
import com.rinoimob.domain.repository.LeadRepository;
import com.rinoimob.domain.repository.UserRepository;
import com.rinoimob.domain.repository.WhatsappInstanceRepository;
import com.rinoimob.domain.repository.WhatsappMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WhatsappMessageService {

    private final WhatsappMessageRepository messageRepo;
    private final WhatsappInstanceRepository instanceRepo;
    private final LeadRepository leadRepo;
    private final UserRepository userRepo;
    private final EvolutionApiClient evolutionClient;

    public List<WhatsappMessageResponse> getMessagesForLead(UUID leadId) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        // Verify lead belongs to tenant
        leadRepo.findByIdAndTenantIdAndDeletedAtIsNull(leadId, tenantId)
            .orElseThrow(() -> new RuntimeException("Lead not found"));
        return messageRepo.findByLeadIdOrderByCreatedAtAsc(leadId)
            .stream().map(this::toResponse).toList();
    }

    public WhatsappMessageResponse send(UUID leadId, SendWhatsappMessageRequest req, UUID userId) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());

        Lead lead = leadRepo.findByIdAndTenantIdAndDeletedAtIsNull(leadId, tenantId)
            .orElseThrow(() -> new RuntimeException("Lead not found"));

        if (lead.getPhone() == null || lead.getPhone().isBlank()) {
            throw new RuntimeException("Lead has no phone number");
        }

        WhatsappInstance instance = instanceRepo.findByIdAndTenantId(req.getInstanceId(), tenantId)
            .orElseThrow(() -> new RuntimeException("Instance not found"));

        if (!"CONNECTED".equals(instance.getStatus())) {
            throw new RuntimeException("WhatsApp instance is not connected");
        }

        // Normalize phone: keep digits only, ensure country code
        String toNumber = lead.getPhone().replaceAll("\\D", "");

        String externalId = evolutionClient.sendText(instance.getInstanceName(), toNumber, req.getText());

        WhatsappMessage message = new WhatsappMessage();
        message.setTenantId(tenantId);
        message.setLeadId(leadId);
        message.setInstanceId(instance.getId());
        message.setDirection("OUTBOUND");
        message.setContent(req.getText());
        message.setSentByUserId(userId);
        message.setExternalMessageId(externalId);
        message.setStatus("SENT");
        message = messageRepo.save(message);

        return toResponse(message);
    }

    public WhatsappMessageResponse sendToNumber(String phoneNumber, UUID instanceId, String text, UUID userId) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());

        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new RuntimeException("Phone number is required");
        }

        WhatsappInstance instance = instanceRepo.findByIdAndTenantId(instanceId, tenantId)
            .orElseThrow(() -> new RuntimeException("Instance not found"));

        if (!"CONNECTED".equals(instance.getStatus())) {
            throw new RuntimeException("WhatsApp instance is not connected");
        }

        String toNumber = phoneNumber.replaceAll("\\D", "");

        String externalId = evolutionClient.sendText(instance.getInstanceName(), toNumber, text);

        WhatsappMessage message = new WhatsappMessage();
        message.setTenantId(tenantId);
        message.setLeadId(null);
        message.setInstanceId(instance.getId());
        message.setDirection("OUTBOUND");
        message.setContent(text);
        message.setSentByUserId(userId);
        message.setExternalMessageId(externalId);
        message.setStatus("SENT");
        message = messageRepo.save(message);

        return toResponse(message);
    }

    public WhatsappMessageResponse toResponse(WhatsappMessage m) {
        WhatsappMessageResponse r = new WhatsappMessageResponse();
        r.setId(m.getId());
        r.setDirection(m.getDirection());
        r.setContent(m.getContent());
        r.setStatus(m.getStatus());
        r.setInstanceId(m.getInstanceId());
        r.setCreatedAt(m.getCreatedAt());
        r.setSentByUserId(m.getSentByUserId());

        // Enrich instance display name
        instanceRepo.findById(m.getInstanceId()).ifPresent(inst ->
            r.setInstanceDisplayName(inst.getDisplayName()));

        // Enrich sender name
        if (m.getSentByUserId() != null) {
            userRepo.findById(m.getSentByUserId()).ifPresent(u ->
                r.setSentByUserName(u.getFirstName() + " " + u.getLastName()));
        }
        return r;
    }
}
