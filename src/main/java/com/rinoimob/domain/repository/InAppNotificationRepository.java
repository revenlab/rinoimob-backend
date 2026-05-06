package com.rinoimob.domain.repository;

import com.rinoimob.domain.entity.InAppNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InAppNotificationRepository extends JpaRepository<InAppNotification, UUID> {

    /**
     * Find all notifications for a user in a tenant, paginated.
     */
    @Query("SELECT n FROM InAppNotification n WHERE n.tenantId = :tenantId AND n.recipientId = :recipientId ORDER BY n.createdAt DESC")
    Page<InAppNotification> findByTenantIdAndRecipientId(
            @Param("tenantId") UUID tenantId,
            @Param("recipientId") UUID recipientId,
            Pageable pageable
    );

    /**
     * Find unread notifications for a user in a tenant.
     */
    @Query("SELECT n FROM InAppNotification n WHERE n.tenantId = :tenantId AND n.recipientId = :recipientId AND n.isRead = false ORDER BY n.createdAt DESC")
    List<InAppNotification> findUnreadByTenantIdAndRecipientId(
            @Param("tenantId") UUID tenantId,
            @Param("recipientId") UUID recipientId
    );

    /**
     * Count unread notifications for a user in a tenant.
     */
    @Query("SELECT COUNT(n) FROM InAppNotification n WHERE n.tenantId = :tenantId AND n.recipientId = :recipientId AND n.isRead = false")
    long countUnreadByTenantIdAndRecipientId(
            @Param("tenantId") UUID tenantId,
            @Param("recipientId") UUID recipientId
    );

    /**
     * Find a notification by ID, ensuring tenant isolation.
     */
    @Query("SELECT n FROM InAppNotification n WHERE n.id = :id AND n.tenantId = :tenantId")
    InAppNotification findByIdAndTenantId(
            @Param("id") UUID id,
            @Param("tenantId") UUID tenantId
    );

    /**
     * Mark all notifications as read for a user in a tenant.
     */
    @Query("UPDATE InAppNotification n SET n.isRead = true, n.readAt = CURRENT_TIMESTAMP WHERE n.tenantId = :tenantId AND n.recipientId = :recipientId AND n.isRead = false")
    void markAllAsRead(
            @Param("tenantId") UUID tenantId,
            @Param("recipientId") UUID recipientId
    );

    /**
     * Delete old read notifications (older than 30 days).
     */
    @Query("DELETE FROM InAppNotification n WHERE n.tenantId = :tenantId AND n.isRead = true AND n.readAt < DATE_SUB(CURRENT_TIMESTAMP, 30 DAY)")
    void deleteOldReadNotifications(@Param("tenantId") UUID tenantId);
}
