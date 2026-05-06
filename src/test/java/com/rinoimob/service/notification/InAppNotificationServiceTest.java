package com.rinoimob.service.notification;

import com.rinoimob.domain.dto.InAppNotificationResponse;
import com.rinoimob.domain.entity.InAppNotification;
import com.rinoimob.domain.repository.InAppNotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InAppNotificationServiceTest {

    @Mock
    private InAppNotificationRepository repository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private InAppNotificationService service;

    private final UUID TENANT_ID = UUID.randomUUID();
    private final UUID USER_ID = UUID.randomUUID();
    private final UUID NOTIF_ID = UUID.randomUUID();

    private InAppNotification testNotification;

    @BeforeEach
    void setUp() {
        testNotification = new InAppNotification();
        testNotification.setId(NOTIF_ID);
        testNotification.setTenantId(TENANT_ID);
        testNotification.setRecipientId(USER_ID);
        testNotification.setTitle("Test Notification");
        testNotification.setMessage("This is a test");
        testNotification.setType(InAppNotification.NotificationType.INFO);
        testNotification.setIsRead(false);
        testNotification.setCreatedAt(LocalDateTime.now());
    }

    @Test
    void shouldSendNotificationSuccessfully() throws Exception {
        when(repository.save(any(InAppNotification.class))).thenReturn(testNotification);

        Map<String, Object> metadata = new HashMap<>();
        service.sendNotification(TENANT_ID, USER_ID, "Test", "Message", NotificationService.NotificationType.INFO, metadata);

        verify(repository, times(1)).save(any(InAppNotification.class));
        verify(messagingTemplate, times(1)).convertAndSend(anyString(), any(InAppNotificationResponse.class));
    }

    @Test
    void shouldGetUnreadNotifications() {
        List<InAppNotification> notifications = Arrays.asList(testNotification);
        when(repository.findUnreadByTenantIdAndRecipientId(TENANT_ID, USER_ID))
            .thenReturn(notifications);

        List<InAppNotificationResponse> result = service.getUnreadNotifications(TENANT_ID, USER_ID);

        assertEquals(1, result.size());
        assertEquals("Test Notification", result.get(0).getTitle());
    }

    @Test
    void shouldCountUnreadNotifications() {
        when(repository.countUnreadByTenantIdAndRecipientId(TENANT_ID, USER_ID))
            .thenReturn(5L);

        Map<String, Long> stats = service.getNotificationStats(TENANT_ID, USER_ID);

        assertEquals(5L, stats.get("unreadCount"));
    }

    @Test
    void shouldMarkNotificationAsRead() {
        when(repository.findByIdAndTenantId(NOTIF_ID, TENANT_ID)).thenReturn(testNotification);
        when(repository.save(any(InAppNotification.class))).thenReturn(testNotification);

        InAppNotificationResponse response = service.markAsRead(TENANT_ID, NOTIF_ID);

        assertNotNull(response);
        assertEquals("Test Notification", response.getTitle());
    }

    @Test
    void shouldThrowExceptionWhenNotificationNotFound() {
        when(repository.findByIdAndTenantId(NOTIF_ID, TENANT_ID)).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () -> service.markAsRead(TENANT_ID, NOTIF_ID));
    }

    @Test
    void shouldDeleteNotification() {
        when(repository.findByIdAndTenantId(NOTIF_ID, TENANT_ID)).thenReturn(testNotification);

        service.deleteNotification(TENANT_ID, NOTIF_ID);

        verify(repository, times(1)).delete(testNotification);
    }
}
