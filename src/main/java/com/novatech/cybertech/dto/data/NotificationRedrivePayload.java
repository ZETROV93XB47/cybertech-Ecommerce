package com.novatech.cybertech.dto.data;

import com.novatech.cybertech.entities.enums.CommunicationChanel;
import com.novatech.cybertech.entities.enums.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * A small, redrive-friendly snapshot of a {@link NotificationContext}.
 *
 * <p><b>Why a separate DTO?</b> The full {@link NotificationContext} is generic
 * over an unbounded payload type, and its {@code data} map is populated as the
 * context flows through the strategy chain (see
 * {@link com.novatech.cybertech.services.implementation.OrderConfirmationNotification},
 * which mutates {@code data} with theme colours, computed titles, etc.). That
 * makes it brittle to round-trip through Jackson 3 — the field types are not
 * statically known and the map can carry already-computed presentation values
 * we don't want frozen into a redrive blob.
 *
 * <p>{@code NotificationRedrivePayload} captures only the inputs that are
 * required to <em>rebuild</em> a {@link NotificationContext} from scratch in
 * the Phase 3 batch tasklet:
 * <ul>
 *   <li>{@link #notificationType} — picks the {@code AbstractNotification} strategy</li>
 *   <li>{@link #communicationChanel} — picks the {@code NotificationProcessor}</li>
 *   <li>{@link #subject} / {@link #templatePath} — already-resolved presentation hints</li>
 *   <li>{@link #userContact} — recipient + preferred channel</li>
 *   <li>{@link #payload} — the typed payload (round-tripped via the
 *       polymorphic discriminator on {@link NotificationPayload})</li>
 *   <li>{@link #data} — pristine, pre-strategy data map (kept SMALL on
 *       purpose; the strategies will repopulate it during redrive)</li>
 * </ul>
 *
 * <p>Persisted as JSON in the {@code payload} column of
 * {@link com.novatech.cybertech.entities.NotificationEntity} by
 * {@link com.novatech.cybertech.services.implementation.NotificationOutcomeRecorder}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationRedrivePayload {

    private NotificationType notificationType;
    private CommunicationChanel communicationChanel;
    private String subject;
    private String templatePath;
    private UserContactDto userContact;
    private NotificationPayload payload;

    /**
     * Pre-strategy data map. We deliberately do NOT capture the post-strategy
     * mutated map — see class javadoc.
     */
    @Builder.Default
    private Map<String, Object> data = new HashMap<>();

    /**
     * Build a fresh {@link NotificationContext} suitable for resubmission to
     * {@link com.novatech.cybertech.dispatcher.NotificationDispatcher#dispatch}.
     *
     * <p>The resulting context is {@link com.novatech.cybertech.dto.data.NotificationContext}-typed
     * with a raw {@link NotificationPayload} bound. Strategy implementations
     * cast back to their concrete subtype (see
     * {@code OrderConfirmationNotification.sendNotification}); polymorphic
     * deserialization preserves that subtype.
     */
    public NotificationContext<NotificationPayload> toNotificationContext() {
        return NotificationContext.<NotificationPayload>builder()
                .notificationType(notificationType)
                .communicationChanel(communicationChanel)
                .subject(subject)
                .templatePath(templatePath)
                .user(userContact)
                .payload(payload)
                .data(data == null ? new HashMap<>() : new HashMap<>(data))
                .build();
    }

    /**
     * Best-effort projection from a live {@link NotificationContext} into a
     * persistable redrive snapshot. Tolerates missing fields — the order
     * confirmation path, for example, currently puts its DTO into
     * {@code data} rather than the typed {@code payload} slot.
     */
    public static NotificationRedrivePayload from(final NotificationContext<?> context) {
        if (context == null) {
            return null;
        }
        return NotificationRedrivePayload.builder()
                .notificationType(context.getNotificationType())
                .communicationChanel(context.getCommunicationChanel())
                .subject(context.getSubject())
                .templatePath(context.getTemplatePath())
                .userContact(context.getUser())
                .payload(context.getPayload())
                .data(context.getData() == null ? new HashMap<>() : new HashMap<>(context.getData()))
                .build();
    }
}
