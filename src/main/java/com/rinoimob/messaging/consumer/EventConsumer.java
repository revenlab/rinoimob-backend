package com.rinoimob.messaging.consumer;

import com.rinoimob.config.RabbitMQConfig;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.logging.Logger;

@Component
public class EventConsumer {

    private static final Logger logger = Logger.getLogger(EventConsumer.class.getName());

    @RabbitListener(queues = RabbitMQConfig.EVENTS_QUEUE)
    public void handleEvent(Map<String, Object> message) {
        try {
            String eventType = (String) message.get("eventType");
            String tenantId = (String) message.get("tenantId");

            logger.fine("Received event: " + eventType + " for tenant: " + tenantId);

            switch (eventType) {
                case "TENANT_CREATED":
                    handleTenantCreated(message);
                    break;
                case "TENANT_UPDATED":
                    handleTenantUpdated(message);
                    break;
                case "PROPERTY_CREATED":
                    handlePropertyCreated(message);
                    break;
                default:
                    logger.warning("Unknown event type: " + eventType);
            }
        } catch (Exception e) {
            logger.warning("Error processing event");
        }
    }

    private void handleTenantCreated(Map<String, Object> message) {
        logger.fine("Processing tenant created event");
    }

    private void handleTenantUpdated(Map<String, Object> message) {
        logger.fine("Processing tenant updated event");
    }

    private void handlePropertyCreated(Map<String, Object> message) {
        logger.fine("Processing property created event");
    }
}
