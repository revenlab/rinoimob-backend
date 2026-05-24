package com.rinoimob.service;

import com.rinoimob.domain.dto.LeadResponse;
import com.rinoimob.domain.dto.WsMessageEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LeadRealtimeService {

    private final SimpMessagingTemplate messagingTemplate;

    public void publishLeadCreated(UUID tenantId, LeadResponse lead) {
        publish(tenantId, new WsMessageEvent("LEAD_CREATED", lead));
    }

    public void publishLeadUpdated(UUID tenantId, LeadResponse lead) {
        publish(tenantId, new WsMessageEvent("LEAD_UPDATED", lead));
    }

    public void publishLeadDeleted(UUID tenantId, UUID leadId) {
        publish(tenantId, new WsMessageEvent("LEAD_DELETED", Map.of("id", leadId.toString())));
    }

    private void publish(UUID tenantId, WsMessageEvent event) {
        try {
            messagingTemplate.convertAndSend("/topic/" + tenantId + ".leads", event);
        } catch (Exception e) {
            log.warn("Failed to publish lead WS event tenant={} type={}: {}", tenantId, event.getType(), e.getMessage());
        }
    }
}
