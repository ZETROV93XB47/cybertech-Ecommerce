package com.novatech.cybertech.entities;

import com.novatech.cybertech.entities.enums.CommunicationChanel;
import com.novatech.cybertech.entities.enums.NotificationStatus;
import com.novatech.cybertech.entities.enums.NotificationType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@Table(
        name = "notificationTable",
        indexes = {
                @Index(name = "idx_notification_status", columnList = "status"),
                @Index(name = "idx_notification_order_uuid", columnList = "orderUuid"),
                @Index(name = "idx_notification_type_status", columnList = "notificationType,status")
        }
)
public class NotificationEntity extends BaseEntity<Long> {

    @Column(name = "orderUuid", nullable = false)
    private UUID orderUuid;

    @Column(name = "notificationType", nullable = false)
    @Enumerated(EnumType.STRING)
    private NotificationType notificationType;

    @Column(name = "communicationChannel", nullable = false)
    @Enumerated(EnumType.STRING)
    private CommunicationChanel communicationChannel;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private NotificationStatus status;

    @Column(name = "recipient", nullable = false)
    private String recipient;

    @Column(name = "retryCount", nullable = false)
    private Integer retryCount;

    @Column(name = "lastAttemptAt")
    private LocalDateTime lastAttemptAt;

    @Column(name = "sentAt")
    private LocalDateTime sentAt;

    @Column(name = "errorMessage", length = 1000)
    private String errorMessage;
}