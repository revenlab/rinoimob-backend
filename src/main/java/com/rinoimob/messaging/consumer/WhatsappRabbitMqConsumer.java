package com.rinoimob.messaging.consumer;

import com.rinoimob.config.WhatsappRabbitMQConfig;
import com.rinoimob.domain.entity.WhatsappMessage;
import com.rinoimob.domain.repository.LeadRepository;
import com.rinoimob.domain.repository.WhatsappInstanceRepository;
import com.rinoimob.domain.repository.WhatsappMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

@Component
@RequiredArgsConstructor
public class WhatsappRabbitMqConsumer {

    private static final Logger log = Logger.getLogger(WhatsappRabbitMqConsumer.class.getName());

    private final WhatsappInstanceRepository instanceRepo;
    private final WhatsappMessageRepository messageRepo;
    private final LeadRepository leadRepo;

    @RabbitListener(queues = WhatsappRabbitMQConfig.MESSAGES_QUEUE)
    @SuppressWarnings("unchecked")
    public void handleMessageUpsert(Map<String, Object> payload) {
        try {
            String instanceName = (String) payload.get("instance");
            if (instanceName == null) return;

            instanceRepo.findByInstanceName(instanceName).ifPresent(instance -> {
                Object dataObj = payload.get("data");
                if (!(dataObj instanceof Map)) return;
                Map<String, Object> data = (Map<String, Object>) dataObj;

                Object keyObj = data.get("key");
                if (!(keyObj instanceof Map)) return;
                Map<String, Object> key = (Map<String, Object>) keyObj;

                // Skip messages sent by us
                if (Boolean.TRUE.equals(key.get("fromMe"))) return;

                String remoteJid = (String) key.get("remoteJid");
                if (remoteJid == null || remoteJid.contains("@g.us")) return; // skip groups

                String fromNumber = remoteJid.replace("@s.whatsapp.net", "").replaceAll("\\D", "");

                String text = extractText(data);
                if (text == null || text.isBlank()) return;

                // Match lead by phone within the tenant
                UUID leadId = leadRepo.findByTenantIdAndDeletedAtIsNull(instance.getTenantId()).stream()
                    .filter(l -> l.getPhone() != null && (
                        l.getPhone().replaceAll("\\D", "").endsWith(fromNumber) ||
                        fromNumber.endsWith(l.getPhone().replaceAll("\\D", ""))))
                    .findFirst()
                    .map(l -> l.getId())
                    .orElse(null);

                WhatsappMessage msg = new WhatsappMessage();
                msg.setTenantId(instance.getTenantId());
                msg.setLeadId(leadId);
                msg.setInstanceId(instance.getId());
                msg.setDirection("INBOUND");
                msg.setContent(text);
                msg.setStatus("RECEIVED");
                messageRepo.save(msg);
            });
        } catch (Exception e) {
            log.warning("Error processing WhatsApp message: " + e.getMessage());
        }
    }

    @RabbitListener(queues = WhatsappRabbitMQConfig.CONNECTION_QUEUE)
    @SuppressWarnings("unchecked")
    public void handleConnectionUpdate(Map<String, Object> payload) {
        try {
            String instanceName = (String) payload.get("instance");
            if (instanceName == null) return;

            Object dataObj = payload.get("data");
            if (!(dataObj instanceof Map)) return;
            Map<String, Object> data = (Map<String, Object>) dataObj;
            String state = (String) data.get("state"); // open | close | connecting

            instanceRepo.findByInstanceName(instanceName).ifPresent(instance -> {
                String newStatus = switch (state != null ? state : "") {
                    case "open" -> "CONNECTED";
                    case "connecting" -> "CONNECTING";
                    default -> "DISCONNECTED";
                };
                instance.setStatus(newStatus);
                instanceRepo.save(instance);
            });
        } catch (Exception e) {
            log.warning("Error processing connection update: " + e.getMessage());
        }
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
