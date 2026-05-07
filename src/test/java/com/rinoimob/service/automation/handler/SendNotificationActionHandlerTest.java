package com.rinoimob.service.automation.handler;

import com.rinoimob.context.TenantContext;
import com.rinoimob.service.notification.EmailNotificationService;
import com.rinoimob.service.notification.InAppNotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SendNotificationActionHandlerTest {

    @Mock
    private EmailNotificationService emailNotificationService;

    @Mock
    private InAppNotificationService inAppNotificationService;

    @InjectMocks
    private SendNotificationActionHandler handler;

    private final String TEST_TENANT_ID = "12345678-1234-1234-1234-123456789012";
    private final String TEST_USER_ID = "87654321-4321-4321-4321-210987654321";

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TEST_TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void shouldGenerateDefaultTitleWhenMissing() throws Exception {
        Map<String, Object> actionData = new HashMap<>();
        actionData.put("message", "Test message");
        actionData.put("channel", "in-app");
        actionData.put("userId", TEST_USER_ID);

        Map<String, Object> context = new HashMap<>();
        Map<String, Object> resultData = new HashMap<>();

        handler.execute(actionData, context, resultData);

        assertTrue((Boolean) resultData.get("notification_sent"));
        assertNotNull(resultData.get("notification_title"));
        assertEquals("Notification", resultData.get("notification_title"));
    }

    @Test
    void shouldGenerateDefaultMessageWhenMissing() throws Exception {
        Map<String, Object> actionData = new HashMap<>();
        actionData.put("title", "Test Title");
        actionData.put("channel", "in-app");
        actionData.put("userId", TEST_USER_ID);

        Map<String, Object> context = new HashMap<>();
        Map<String, Object> resultData = new HashMap<>();

        handler.execute(actionData, context, resultData);

        assertTrue((Boolean) resultData.get("notification_sent"));
        assertNotNull(resultData.get("notification_title"));
    }

    @Test
    void shouldSendEmailNotificationSuccessfully() throws Exception {
        String email = "test@example.com";
        String title = "Test Notification";
        String message = "This is a test message";

        Map<String, Object> actionData = new HashMap<>();
        actionData.put("title", title);
        actionData.put("message", message);
        actionData.put("channel", "email");
        actionData.put("email", email);

        Map<String, Object> context = new HashMap<>();
        Map<String, Object> resultData = new HashMap<>();

        handler.execute(actionData, context, resultData);

        assertTrue((Boolean) resultData.get("notification_sent"));
        assertEquals(title, resultData.get("notification_title"));
        assertEquals("email", resultData.get("notification_channel"));
        assertEquals(email, resultData.get("notification_recipient_email"));
    }

    @Test
    void shouldSendInAppNotificationSuccessfully() throws Exception {
        String title = "Test In-App Notification";
        String message = "This is an in-app test message";

        Map<String, Object> actionData = new HashMap<>();
        actionData.put("title", title);
        actionData.put("message", message);
        actionData.put("channel", "in-app");
        actionData.put("userId", TEST_USER_ID);

        Map<String, Object> context = new HashMap<>();
        Map<String, Object> resultData = new HashMap<>();

        handler.execute(actionData, context, resultData);

        assertTrue((Boolean) resultData.get("notification_sent"));
        assertEquals(title, resultData.get("notification_title"));
        assertEquals("in-app", resultData.get("notification_channel"));
    }

    @Test
    void shouldDefaultToInAppChannelWhenNotSpecified() throws Exception {
        String title = "Test Notification";
        String message = "Default channel test";

        Map<String, Object> actionData = new HashMap<>();
        actionData.put("title", title);
        actionData.put("message", message);
        actionData.put("userId", TEST_USER_ID);

        Map<String, Object> context = new HashMap<>();
        Map<String, Object> resultData = new HashMap<>();

        handler.execute(actionData, context, resultData);

        assertTrue((Boolean) resultData.get("notification_sent"));
        assertEquals("in-app", resultData.get("notification_channel"));
    }

    @Test
    void shouldHandleEmailNotificationWhenEmailIsMissing() throws Exception {
        String title = "Test Notification";
        String message = "Test message";

        Map<String, Object> actionData = new HashMap<>();
        actionData.put("title", title);
        actionData.put("message", message);
        actionData.put("channel", "email");

        Map<String, Object> context = new HashMap<>();
        Map<String, Object> resultData = new HashMap<>();

        handler.execute(actionData, context, resultData);

        assertFalse((Boolean) resultData.get("notification_sent"));
        assertEquals("Email address is required for email channel", resultData.get("notification_error"));
    }

    @Test
    void shouldHandleInAppNotificationWhenUserIdIsMissing() throws Exception {
        String title = "Test Notification";
        String message = "Test message";

        Map<String, Object> actionData = new HashMap<>();
        actionData.put("title", title);
        actionData.put("message", message);
        actionData.put("channel", "in-app");

        Map<String, Object> context = new HashMap<>();
        Map<String, Object> resultData = new HashMap<>();

        handler.execute(actionData, context, resultData);

        assertFalse((Boolean) resultData.get("notification_sent"));
        assertEquals("User ID is required for in-app channel", resultData.get("notification_error"));
    }
}
