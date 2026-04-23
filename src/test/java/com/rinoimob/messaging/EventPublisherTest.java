package com.rinoimob.messaging;

import com.rinoimob.messaging.publisher.EventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;

@SpringBootTest
class EventPublisherTest {

    @Autowired
    private EventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
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
            Map<String, Object> payload = new HashMap<>();
            payload.put("index", i);

            assertThatCode(() -> eventPublisher.publishEvent("TEST_EVENT_" + i, payload))
                    .doesNotThrowAnyException();
        }
    }
}
