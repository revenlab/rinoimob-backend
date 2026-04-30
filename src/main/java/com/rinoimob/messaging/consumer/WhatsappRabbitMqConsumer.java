package com.rinoimob.messaging.consumer;

import com.rinoimob.config.WhatsappRabbitMQConfig;
import com.rinoimob.domain.dto.WhatsappMessageResponse;
import com.rinoimob.domain.dto.WsMessageEvent;
import com.rinoimob.domain.entity.WhatsappMessage;
import com.rinoimob.domain.repository.LeadRepository;
import com.rinoimob.domain.repository.WhatsappInstanceRepository;
import com.rinoimob.domain.repository.WhatsappMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class WhatsappRabbitMqConsumer {

    private final WhatsappInstanceRepository instanceRepo;
    private final WhatsappMessageRepository messageRepo;
    private final LeadRepository leadRepo;
    private final SimpMessagingTemplate messagingTemplate;

    @RabbitListener(queues = WhatsappRabbitMQConfig.MESSAGES_QUEUE)
    @SuppressWarnings("unchecked")
    public void handleEvent(Map<String, Object> payload) {
        try {
            String instanceName = (String) payload.get("instance");
            if (instanceName == null) return;

            log.debug("Received Evolution event '{}' for instance '{}'", payload.get("event"), instanceName);
            handleMessageUpsert(instanceName, payload);
        } catch (Exception e) {
            log.warn("Error processing Evolution event: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void handleMessageUpsert(String instanceName, Map<String, Object> payload) {
        instanceRepo.findByInstanceName(instanceName).ifPresent(instance -> {
            Object dataObj = payload.get("data");
            List<Map<String, Object>> messages;
            if (dataObj instanceof List) {
                messages = (List<Map<String, Object>>) dataObj;
            } else if (dataObj instanceof Map) {
                messages = List.of((Map<String, Object>) dataObj);
            } else {
                log.warn("Unexpected data type in MESSAGES_UPSERT: {}", dataObj != null ? dataObj.getClass() : "null");
                return;
            }

            for (Map<String, Object> data : messages) {
                Object keyObj = data.get("key");
                if (!(keyObj instanceof Map)) continue;
                Map<String, Object> key = (Map<String, Object>) keyObj;

                if (Boolean.TRUE.equals(key.get("fromMe"))) continue;

                String remoteJid = (String) key.get("remoteJid");
                if (remoteJid == null || remoteJid.contains("@g.us")) continue;

                String fromNumber = remoteJid.replace("@s.whatsapp.net", "").replaceAll("\\D", "");
                String text = extractText(data);
                if (text == null || text.isBlank()) continue;

                UUID leadId = leadRepo.findByTenantIdAndDeletedAtIsNull(instance.getTenantId()).stream()
                    .filter(l -> l.getPhone() != null && (
                        l.getPhone().replaceAll("\\D", "").endsWith(fromNumber) ||
                        fromNumber.endsWith(l.getPhone().replaceAll("\\D", ""))))
                    .findFirst()
                    .map(l -> l.getId())
                    .orElse(null);

                // Resolve lead name for notification display
                String leadName = leadId != null
                    ? leadRepo.findById(leadId).map(l -> l.getName()).orElse(null)
                    : null;

                WhatsappMessage msg = new WhatsappMessage();
                msg.setTenantId(instance.getTenantId());
                msg.setLeadId(leadId);
                msg.setInstanceId(instance.getId());
                msg.setDirection("INBOUND");
                msg.setContent(text);
                msg.setStatus("RECEIVED");
                messageRepo.save(msg);

                // Push to WebSocket subscribers — enrich with fromNumber and leadName
                Map<String, Object> wsPayload = Map.of(
                    "id", msg.getId().toString(),
                    "direction", "INBOUND",
                    "content", text,
                    "status", "RECEIVED",
                    "instanceId", msg.getInstanceId().toString(),
                    "leadId", leadId != null ? leadId.toString() : "",
                    "fromNumber", fromNumber,
                    "senderName", leadName != null ? leadName : ("+" + fromNumber),
                    "createdAt", msg.getCreatedAt().toString()
                );
                WsMessageEvent event = new WsMessageEvent("WHATSAPP_MESSAGE", wsPayload);

                if (msg.getLeadId() != null) {
                    messagingTemplate.convertAndSend(
                        "/topic/" + instance.getTenantId() + ".whatsapp.lead." + msg.getLeadId(), event);
                }
                messagingTemplate.convertAndSend(
                    "/topic/" + instance.getTenantId() + ".whatsapp", event);

                log.info("Saved INBOUND message from {} for instance {}", fromNumber, instanceName);
            }
        });
    }

    @RabbitListener(queues = WhatsappRabbitMQConfig.CONNECTION_QUEUE)
    @SuppressWarnings("unchecked")
    public void handleConnectionEvent(Map<String, Object> payload) {
        try {
            String instanceName = (String) payload.get("instance");
            if (instanceName == null) return;
            handleConnectionUpdate(instanceName, payload);
        } catch (Exception e) {
            log.warn("Error processing connection update: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void handleConnectionUpdate(String instanceName, Map<String, Object> payload) {
        Object dataObj = payload.get("data");
        if (!(dataObj instanceof Map)) return;
        Map<String, Object> data = (Map<String, Object>) dataObj;
        String state = (String) data.get("state");

        instanceRepo.findByInstanceName(instanceName).ifPresent(instance -> {
            String newStatus = switch (state != null ? state : "") {
                case "open" -> "CONNECTED";
                case "connecting" -> "CONNECTING";
                default -> "DISCONNECTED";
            };
            instance.setStatus(newStatus);
            instanceRepo.save(instance);
            log.info("Instance {} status updated to {}", instanceName, newStatus);
        });
    }

    @SuppressWarnings("unchecked")
    private String extractText(Map<String, Object> data) {
        Object msgObj = data.get("message");
        if (!(msgObj instanceof Map)) return null;
        Map<String, Object> message = (Map<String, Object>) msgObj;
        // Plain text
        if (message.get("conversation") instanceof String s) return s;
        // Extended text
        if (message.get("extendedTextMessage") instanceof Map ext) {
            return (String) ((Map<String, Object>) ext).get("text");
        }
        return null;
    }
}
