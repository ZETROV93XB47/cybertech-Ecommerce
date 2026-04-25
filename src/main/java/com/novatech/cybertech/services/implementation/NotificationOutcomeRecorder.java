package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.dto.data.NotificationContext;
import com.novatech.cybertech.dto.data.NotificationRedrivePayload;
import com.novatech.cybertech.dto.data.OrderEventDto;
import com.novatech.cybertech.dto.data.UserContactDto;
import com.novatech.cybertech.entities.NotificationEntity;
import com.novatech.cybertech.entities.enums.NotificationStatus;
import com.novatech.cybertech.repositories.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Single point of truth for {@link NotificationEntity} audit rows.
 *
 * <p>All listeners ({@link com.novatech.cybertech.listener.NotificationListener},
 * {@link com.novatech.cybertech.listener.OrderEventListener}) and — once
 * Phase 2 lands — the dispatcher itself route through this recorder so that
 * persistence semantics are uniform across {@code ORDER_CONFIRMATION},
 * {@code ORDER_UPDATE}, and {@code SHIPPING_CONFIRMATION} paths. Before
 * Phase 1 only the shipping listener was persisting an audit row, leaving the
 * order paths invisible to ops if delivery failed (the asymmetric-persistence
 * gap noted in the Phase 1 spec).
 *
 * <p><b>Design note:</b> the recorder NEVER throws. Persistence — even of a
 * row whose redrive payload could not be serialized — must succeed best-effort
 * because losing an audit row is strictly worse than persisting one with a
 * null {@code payload}. Failures during JSON serialization are logged at WARN
 * but the row is still saved. Failures during the actual repository save are
 * NOT swallowed because they indicate an infrastructure problem the caller
 * should surface; however the listeners themselves catch broadly so an async
 * commit-side error never propagates back into the originating thread.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationOutcomeRecorder {

    /**
     * Cap on the audit error message — matches the {@code length=1000} on
     * {@link NotificationEntity#getErrorMessage()}. Exceeding this would
     * trigger a JPA constraint violation at flush time, defeating the
     * recorder's "always persist" contract.
     */
    private static final int ERROR_MESSAGE_MAX_LENGTH = 1000;

    private final NotificationRepository notificationRepository;
    private final ObjectMapper objectMapper;

    /**
     * Persist a {@link NotificationEntity} audit row reflecting the outcome of
     * a single dispatch (or a final outcome after all in-process retries).
     *
     * <p>Behaviour:
     * <ul>
     *   <li>{@code orderUuid} is extracted from the typed payload (e.g.
     *       {@link ShippingConfirmationPayload}) when available; otherwise
     *       from the {@code OrderEventDto} stashed in
     *       {@link NotificationContext#getData()} (the order-confirmation /
     *       order-update path); otherwise {@code null} (the column is now
     *       nullable — see {@link NotificationEntity}).</li>
     *   <li>{@code lastAttemptAt} is always set to {@link LocalDateTime#now()}.</li>
     *   <li>{@code sentAt} is set iff {@code status == SENT}.</li>
     *   <li>{@code errorMessage} is set from {@code failure.getMessage()}
     *       truncated to fit the existing column length.</li>
     *   <li>The {@link NotificationRedrivePayload} snapshot is JSON-serialized
     *       into the {@code payload} column. If serialization itself throws,
     *       a WARN is logged and the row is persisted with {@code payload=null}.</li>
     * </ul>
     *
     * @param context     the dispatch context that drove the attempt — must
     *                    not be null
     * @param status      the outcome to record
     * @param retryCount  the number of <em>failed</em> attempts so far (0 if
     *                    the very first attempt succeeded)
     * @param failure     the terminal failure that caused {@code FAILED} /
     *                    {@code PENDING_RETRY}, or {@code null} on success
     * @return the persisted entity (with id populated)
     */
    public NotificationEntity recordOutcome(
            final NotificationContext<?> context,
            final NotificationStatus status,
            final int retryCount,
            @Nullable final Throwable failure) {

        final UserContactDto user = context.getUser();
        final UUID orderUuid = extractOrderUuid(context);
        final String serializedPayload = serializeRedrivePayload(context);
        final LocalDateTime now = LocalDateTime.now();

        final NotificationEntity entity = NotificationEntity.builder()
                .orderUuid(orderUuid)
                .notificationType(context.getNotificationType())
                // Prefer the channel from the context (the listener may have
                // overridden it). Fall back to the user's default to keep the
                // column non-null even if a context arrives malformed.
                .communicationChannel(context.getCommunicationChanel() != null
                        ? context.getCommunicationChanel()
                        : (user != null ? user.getDefaultCommunicationChanel() : null))
                .status(status)
                .recipient(user != null ? user.getEmail() : null)
                .retryCount(retryCount)
                .lastAttemptAt(now)
                .sentAt(status == NotificationStatus.SENT ? now : null)
                .errorMessage(truncate(failure != null ? failure.getMessage() : null))
                .payload(serializedPayload)
                .build();

        return notificationRepository.save(entity);
    }

    /**
     * Try the typed payload first (clean source of truth — what
     * {@link com.novatech.cybertech.listener.NotificationListener} populates),
     * then fall back to the data-map shape used by the order-event path.
     */
    private static UUID extractOrderUuid(final NotificationContext<?> context) {
        if (context.getPayload() instanceof ShippingConfirmationPayload shipping) {
            return shipping.getOrderUuid();
        }

        // OrderEventListener stashes the OrderEventDto under this key; see
        // OrderEventListener.onOrderCreated / onOrderUpdated.
        if (context.getData() != null) {
            final Object eventDto = context.getData().get("orderEventDto");
            if (eventDto instanceof OrderEventDto orderEventDto) {
                return orderEventDto.getOrderUuid();
            }
        }

        return null;
    }

    /**
     * Serialize the redrive snapshot, never throwing back to the caller.
     * A null return means "we lost the redrive trace for this row" — the
     * audit row will still be persisted.
     */
    private String serializeRedrivePayload(final NotificationContext<?> context) {
        try {
            final NotificationRedrivePayload snapshot = NotificationRedrivePayload.from(context);
            return snapshot == null ? null : objectMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            // Defensive: a Jackson serialization failure (e.g. an
            // unrecognized polymorphic subtype, a Hibernate proxy
            // sneaking into the payload graph) MUST NOT cascade and prevent
            // the audit row from being written. Log loudly so the failure
            // is visible to ops and move on.
            log.warn("Failed to serialize NotificationRedrivePayload for type={} channel={}; persisting row with payload=null",
                    context.getNotificationType(),
                    context.getCommunicationChanel(),
                    e);
            return null;
        }
    }

    private static String truncate(final String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= ERROR_MESSAGE_MAX_LENGTH
                ? message
                : message.substring(0, ERROR_MESSAGE_MAX_LENGTH);
    }
}
