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
 * over an unbounded payload type. This class captures only the inputs required
 * to rebuild a {@link NotificationContext} from scratch in the Phase 3 batch
 * tasklet:
 * <ul>
 *   <li>{@link #notificationType} — picks the {@code AbstractNotification} strategy</li>
 *   <li>{@link #communicationChanel} — picks the {@code NotificationProcessor}</li>
 *   <li>{@link #subject} / {@link #templatePath} — presentation hints already resolved</li>
 *   <li>{@link #userContact} — recipient + preferred channel</li>
 *   <li>{@link #payload} — the typed payload (round-tripped via the
 *       polymorphic discriminator on {@link NotificationPayload})</li>
 *   <li>{@link #templateVariables} — the post-{@code prepareContext} template model,
 *       captured as a convenience. On redrive {@code prepareContext} is called
 *       again and overwrites these values, so the captured map is redundant but
 *       harmless. It is kept because it makes the persisted JSON human-readable
 *       for ops triage without having to re-run the strategy mentally.</li>
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
     * Post-{@code prepareContext} template variables snapshot. On redrive the
     * strategy's {@code prepareContext} repopulates them, so this field is
     * kept for observability only.
     */
    @Builder.Default
    private Map<String, Object> templateVariables = new HashMap<>();

    /**
     * Build a fresh {@link NotificationContext} suitable for resubmission to
     * {@link com.novatech.cybertech.dispatcher.NotificationDispatcher#dispatch}.
     */
    public NotificationContext<NotificationPayload> toNotificationContext() {
        return NotificationContext.<NotificationPayload>builder()
                .notificationType(notificationType)
                .communicationChanel(communicationChanel)
                .subject(subject)
                .templatePath(templatePath)
                .user(userContact)
                .payload(payload)
                .templateVariables(templateVariables == null ? new HashMap<>() : new HashMap<>(templateVariables))
                .build();
    }

    /**
     * Best-effort projection from a live {@link NotificationContext} into a
     * persistable redrive snapshot. Tolerates a null context (returns null).
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
                .templateVariables(context.getTemplateVariables() == null
                        ? new HashMap<>()
                        : new HashMap<>(context.getTemplateVariables()))
                .build();
    }
}
