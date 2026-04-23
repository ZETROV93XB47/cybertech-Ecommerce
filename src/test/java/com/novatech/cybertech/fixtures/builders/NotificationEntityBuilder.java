package com.novatech.cybertech.fixtures.builders;

import com.novatech.cybertech.entities.NotificationEntity;
import com.novatech.cybertech.entities.enums.CommunicationChanel;
import com.novatech.cybertech.entities.enums.NotificationStatus;
import com.novatech.cybertech.entities.enums.NotificationType;

import java.util.UUID;

/**
 * Test fixture builder for {@link NotificationEntity}. Presets {@code uuid} explicitly because
 * builders bypass {@code BaseEntity#prePersist}.
 */
public final class NotificationEntityBuilder {

    private NotificationEntityBuilder() {
    }

    public static NotificationEntity aValidNotification() {
        return aValidNotificationBuilder().build();
    }

    public static NotificationEntity.NotificationEntityBuilder<?, ?> aValidNotificationBuilder() {
        return NotificationEntity.builder()
                .uuid(UUID.randomUUID())
                .orderUuid(UUID.randomUUID())
                .notificationType(NotificationType.ORDER_CONFIRMATION)
                .communicationChannel(CommunicationChanel.EMAIL)
                .status(NotificationStatus.PENDING)
                .recipient("user@example.com")
                .retryCount(0);
    }
}
