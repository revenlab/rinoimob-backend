package com.rinoimob.messaging;

import com.rinoimob.messaging.publisher.EventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;

@ExtendWith(MockitoExtension.class)
class EventPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    private EventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        eventPublisher = new EventPublisher(rabbitTemplate);
    }

    @Test
    void testPublishEvent() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("key", "value");

        assertThatCode(() -> eventPublisher.publishEvent("TEST_EVENT", payload))
                .doesNotThrowAnyException();
    }

    @Test
    void testPublishTenantEvent() {
        Map<String, Object> data = new HashMap<>();
        data.put("property", "value");

        assertThatCode(() -> eventPublisher.publishTenantEvent("tenant-123", "TENANT_CREATED", data))
                .doesNotThrowAnyException();
    }

    @Test
    void testPublishMultipleEvents() {
        for (int i = 0; i < 5; i++) {
            int eventIndex = i;
            Map<String, Object> payload = new HashMap<>();
            payload.put("index", eventIndex);

            assertThatCode(() -> eventPublisher.publishEvent("TEST_EVENT_" + eventIndex, payload))
                    .doesNotThrowAnyException();
        }
    }
}
