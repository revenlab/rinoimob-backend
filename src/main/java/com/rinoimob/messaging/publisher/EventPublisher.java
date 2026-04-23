package com.rinoimob.messaging.publisher;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

@Component
public class EventPublisher {

    private static final Logger logger = Logger.getLogger(EventPublisher.class.getName());
    private final RabbitTemplate rabbitTemplate;

    public EventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishEvent(String eventType, Map<String, Object> payload) {
        try {
            String routingKey = "event." + eventType;
            rabbitTemplate.convertAndSend("events.exchange", routingKey, payload);
            logger.fine("Event published: " + eventType);
        } catch (Exception e) {
            logger.warning("Failed to publish event: " + eventType);
        }
    }

    public void publishTenantEvent(String tenantId, String eventType, Map<String, Object> data) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("tenantId", tenantId);
        payload.put("eventType", eventType);
        payload.put("data", data);
        payload.put("timestamp", System.currentTimeMillis());

        publishEvent(eventType, payload);
    }
}
