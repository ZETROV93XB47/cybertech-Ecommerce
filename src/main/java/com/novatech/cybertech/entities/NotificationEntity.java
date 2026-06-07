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
@Setter
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

    /**
     * Linked order UUID. Nullable: extracted from the typed payload
     * ({@link com.novatech.cybertech.dto.data.ShippingConfirmationPayload} or
     * {@link com.novatech.cybertech.dto.data.OrderConfirmationPayload}) by the recorder.
     * A partial audit row without an order UUID is preferable to losing the trace entirely.
     */
    @Column(name = "orderUuid")
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

    /**
     * JSON-serialized {@link com.novatech.cybertech.dto.data.NotificationRedrivePayload}.
     *
     * <p><b>Why is this nullable?</b> Existing dev/staging databases will not
     * have a payload for rows persisted before this column landed, and the
     * Phase 1 recorder is allowed to persist {@code payload=null} when
     * serialization itself blows up — losing the redrive trace must NEVER
     * cause us to drop the audit row.
     *
     * <p><b>Why {@code TEXT} and not the default {@code VARCHAR(255)}?</b>
     * MySQL maps {@code String} to {@code VARCHAR(255)} unless told otherwise;
     * the JSON snapshot routinely exceeds that on real payloads (full
     * shipping-confirmation context with provider + addresses).
     *
     * <p>Phase 3 will scan rows where status is {@code FAILED} or
     * {@code PENDING_RETRY} and {@code payload IS NOT NULL} to drive the batch
     * redrive tasklet.
     */
    @Lob
    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;
}
