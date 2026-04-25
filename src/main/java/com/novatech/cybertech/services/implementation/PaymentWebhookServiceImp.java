package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.dto.request.stripe.StripeWebhookEventDto;
import com.novatech.cybertech.entities.PaymentEntity;
import com.novatech.cybertech.entities.ProcessedWebhookEventEntity;
import com.novatech.cybertech.entities.enums.PaymentAttemptStatus;
import com.novatech.cybertech.events.OrderPaidEvent;
import com.novatech.cybertech.events.PaymentFailedEvent;
import com.novatech.cybertech.events.PaymentRefundedEvent;
import com.novatech.cybertech.events.PaymentSucceededEvent;
import com.novatech.cybertech.exceptions.PaymentNotFoundException;
import com.novatech.cybertech.repositories.PaymentAttemptRepository;
import com.novatech.cybertech.repositories.ProcessedWebhookEventRepository;
import com.novatech.cybertech.services.core.PaymentWebhookService;
import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.model.Charge;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Default implementation of {@link PaymentWebhookService}.
 *
 * <p><b>Idempotency (BUG-170)</b>: every successful invocation appends a
 * {@link ProcessedWebhookEventEntity} keyed on {@code event.getId()}. A subsequent delivery of the
 * same event short-circuits at the very top — no DB mutation, no domain event re-published. The
 * unique constraint on the ledger column also serves as a race-safe second line of defence when
 * two concurrent deliveries beat the existence check.
 *
 * <p><b>Environment safety (BUG-522)</b>: the {@code livemode} flag of every event is matched
 * against the deployment-level {@code stripe.livemode} property. A mismatch is logged at SEVERE
 * (a misconfigured prod-secret leaking into a test env would otherwise mutate real-money
 * payments) and the event is dropped without processing.
 *
 * <p><b>Domain events</b>: {@code payment_intent.succeeded} publishes both
 * {@link PaymentSucceededEvent} (consumed by the order-confirmation listener) and
 * {@link OrderPaidEvent} (consumed by the shipping listener — BUG-171 fix).
 * {@code payment_intent.payment_failed} publishes {@link PaymentFailedEvent} (BUG-520 fix).
 * {@code charge.refunded} publishes {@link PaymentRefundedEvent}.
 *
 * <p><b>Terminal-state guard (BUG-521)</b>: out-of-order Stripe deliveries (e.g. SUCCESS then a
 * delayed FAILED for the same {@code pi_*}) MUST NOT regress a payment row from SUCCESS to FAILED.
 * The failure handler short-circuits in that case.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentWebhookServiceImp implements PaymentWebhookService {

    private static final String CHARGE_REFUNDED = "charge.refunded";
    private static final String PAYMENT_INTENT_SUCCEEDED = "payment_intent.succeeded";
    private static final String PAYMENT_INTENT_PAYMENT_FAILED = "payment_intent.payment_failed";
    private static final String ORDER_UUID_METADATA_KEY = "order_uuid";

    private final ApplicationEventPublisher eventPublisher;
    private final PaymentAttemptRepository attemptRepository;
    private final ProcessedWebhookEventRepository processedWebhookEventRepository;

    /**
     * The expected {@code livemode} value of incoming events. Defaults to {@code false} so any
     * unconfigured (test / dev) deployment refuses to process real-money events. Production must
     * explicitly opt-in via {@code stripe.livemode=true}.
     */
    @Value("${stripe.livemode:false}")
    private boolean expectedLivemode;

    /**
     * Process a Stripe webhook event end-to-end.
     *
     * <p>Order of operations:
     * <ol>
     *   <li>Dedup: short-circuit if {@code event.getId()} is already in the ledger.</li>
     *   <li>Livemode gate: skip the event if its {@code livemode} disagrees with the configured
     *       deployment mode (BUG-522).</li>
     *   <li>Dispatch on {@code event.getType()} to the matching per-type handler.</li>
     *   <li>Append a {@link ProcessedWebhookEventEntity} row to seal the dedup ledger.</li>
     * </ol>
     *
     * <p>The whole flow runs in a single Spring-managed transaction so that the ledger insert and
     * the side-effects of the handler commit atomically. Any thrown exception rolls back BOTH the
     * payment-row update and the ledger insert, preserving a clean retry surface for Stripe.
     *
     * @param event        the parsed Stripe {@link Event}
     * @param eventPayload the raw HTTP body — required because the rich
     *                     {@link StripeWebhookEventDto} (carrying {@code metadata.order_uuid}) is
     *                     parsed from the wire-bytes, not the typed SDK object
     */
    @Override
    @Transactional
    public void handleEvent(final Event event, final String eventPayload) {

        final String stripeEventId = event.getId();

        if (processedWebhookEventRepository.existsByStripeEventId(stripeEventId)) {
            log.info("Stripe webhook dedup hit; skipping already-processed event id={}", stripeEventId);
            return;
        }

        if (!isLivemodeAcceptable(event)) {
            log.error("SEVERE: Stripe livemode mismatch — dropping event id={}, type={}, eventLivemode={}, expectedLivemode={}. " +
                            "A misconfigured webhook secret may be sending production events to a test environment (or vice-versa).",
                    stripeEventId, event.getType(), event.getLivemode(), expectedLivemode);
            return;
        }

        final ObjectMapper mapper = new ObjectMapper();
        final StripeWebhookEventDto stripeWebhookEventDto = mapper.readValue(eventPayload, StripeWebhookEventDto.class);

        boolean handled = false;

        switch (event.getType()) {

            case PAYMENT_INTENT_SUCCEEDED -> {
                handlePaymentSucceeded(event, stripeWebhookEventDto);
                handled = true;
            }

            case PAYMENT_INTENT_PAYMENT_FAILED -> {
                handlePaymentFailed(event, stripeWebhookEventDto);
                handled = true;
            }

            case CHARGE_REFUNDED -> {
                handleRefund(event, stripeWebhookEventDto);
                handled = true;
            }

            default -> log.info("Unhandled Stripe event type: {}", event.getType());
        }

        // Only seal the dedup ledger for handled event types. Unknown event types must remain
        // un-recorded so that, when a handler is later added, Stripe's retries are not silently
        // dropped by the dedup short-circuit at the top of this method.
        if (handled) {
            recordEventAsProcessed(stripeEventId);
        }
    }

    /**
     * Compares the event's {@code livemode} flag against the configured deployment mode. A null
     * {@code livemode} on the event is treated as "test" ({@code false}) since the Stripe API
     * always sets the field for real events; absence indicates a malformed / non-Stripe payload.
     *
     * @param event the parsed Stripe {@link Event}
     * @return {@code true} if the event's livemode matches the deployment, {@code false} otherwise
     */
    private boolean isLivemodeAcceptable(final Event event) {

        final Boolean eventLivemode = event.getLivemode();
        final boolean effective = eventLivemode != null && eventLivemode;
        return effective == expectedLivemode;
    }

    /**
     * Handle {@code payment_intent.succeeded}. Flips the payment row to {@link PaymentAttemptStatus#SUCCESS}
     * (no-op if already SUCCESS), publishes {@link PaymentSucceededEvent} for the order-confirmation
     * listener, and publishes {@link OrderPaidEvent} for the shipping listener (BUG-171 fix).
     *
     * @param event the parsed Stripe {@link Event}
     * @param dto   the rich application-level DTO parsed from the raw payload
     */
    private void handlePaymentSucceeded(final Event event, final StripeWebhookEventDto dto) {

        final PaymentIntent intent = (PaymentIntent) deserializeDataObject(event);

        final String stripePaymentID = intent.getId();
        final PaymentEntity payment = updatePaymentStatusGuarded(stripePaymentID, PaymentAttemptStatus.SUCCESS, event.getId());

        eventPublisher.publishEvent(new PaymentSucceededEvent(dto));

        final UUID orderUuid = resolveOrderUuid(payment, dto);
        if (orderUuid != null) {
            eventPublisher.publishEvent(new OrderPaidEvent(this, orderUuid));
        }
    }

    /**
     * Handle {@code payment_intent.payment_failed}. Includes the BUG-521 terminal-state guard:
     * if the payment row is already {@link PaymentAttemptStatus#SUCCESS} the failure is dropped
     * (out-of-order Stripe delivery — SUCCESS is terminal once the order has been moved to PAID).
     * Otherwise flips the row to {@link PaymentAttemptStatus#FAILED} and publishes
     * {@link PaymentFailedEvent} for the listener that releases the held stock (BUG-520 fix).
     *
     * @param event the parsed Stripe {@link Event}
     * @param dto   the rich application-level DTO parsed from the raw payload
     */
    private void handlePaymentFailed(final Event event, final StripeWebhookEventDto dto) {

        final PaymentIntent intent = (PaymentIntent) deserializeDataObject(event);

        final String stripePaymentID = intent.getId();

        final PaymentEntity existing = attemptRepository.findByStripePaymentID(stripePaymentID)
                .orElseThrow(() -> new PaymentNotFoundException("Payment attempt not found for stripePaymentID: " + stripePaymentID));

        if (existing.getStatus() == PaymentAttemptStatus.SUCCESS) {
            log.warn("BUG-521 guard: ignoring out-of-order failure for already-succeeded payment stripePaymentID={}", stripePaymentID);
            return;
        }

        existing.setStatus(PaymentAttemptStatus.FAILED);
        existing.setProviderEventId(event.getId());
        attemptRepository.save(existing);
        log.info("Payment updated: stripePaymentID={}, status={}", stripePaymentID, PaymentAttemptStatus.FAILED);

        eventPublisher.publishEvent(new PaymentFailedEvent(dto));
    }

    /**
     * Handle {@code charge.refunded}. Flips the payment row to {@link PaymentAttemptStatus#REFUNDED}
     * and publishes {@link PaymentRefundedEvent} for the listener that updates the order to
     * {@code REFUNDED} and releases stock.
     *
     * @param event the parsed Stripe {@link Event}
     * @param dto   the rich application-level DTO parsed from the raw payload
     */
    private void handleRefund(final Event event, final StripeWebhookEventDto dto) {

        final Charge charge = (Charge) deserializeDataObject(event);

        updatePaymentStatusGuarded(charge.getPaymentIntent(), PaymentAttemptStatus.REFUNDED, event.getId());

        eventPublisher.publishEvent(new PaymentRefundedEvent(dto));
    }

    /**
     * Deserializes the {@code event.data.object} into a {@link StripeObject}, tolerating the
     * API-version skew that occurs in practice between a Stripe account's pinned {@code api_version}
     * (set in the dashboard) and the Stripe SDK's bundled API version.
     *
     * <p>{@link EventDataObjectDeserializer#getObject()} returns {@code Optional.empty()} when the
     * event's {@code api_version} differs from the SDK-bundled one — a hard match check that
     * regularly trips the test fixtures and any prod account that hasn't upgraded its api_version
     * to match the latest SDK release. We fall back to {@link EventDataObjectDeserializer#deserializeUnsafe()}
     * which bypasses the api-version match guard. Stripe schemas are forward-compatible by design;
     * an unmatched field would surface as a {@link EventDataObjectDeserializationException} rather
     * than corrupt data.
     *
     * @param event the parsed Stripe {@link Event}
     * @return the deserialized {@link StripeObject} (caller casts to {@link PaymentIntent} or {@link Charge})
     */
    private StripeObject deserializeDataObject(final Event event) {
        final EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        return deserializer.getObject().orElseGet(() -> {
            try {
                return deserializer.deserializeUnsafe();
            } catch (final EventDataObjectDeserializationException ex) {
                throw new IllegalStateException(
                        "Failed to deserialize Stripe event data object (eventId=" + event.getId()
                                + ", type=" + event.getType()
                                + ", eventApiVersion=" + event.getApiVersion() + ")",
                        ex);
            }
        });
    }

    /**
     * Loads the payment row by Stripe payment id, flips its status, and persists the change. Used
     * by the success and refund paths. The failure path uses an inline variant because of its
     * additional terminal-state guard.
     *
     * @param stripePaymentID the {@code pi_*} identifier from the Stripe event
     * @param status          the new {@link PaymentAttemptStatus}
     * @param stripeEventID   the {@code event.getId()} for audit
     * @return the persisted {@link PaymentEntity}
     */
    private PaymentEntity updatePaymentStatusGuarded(final String stripePaymentID, final PaymentAttemptStatus status, final String stripeEventID) {

        final PaymentEntity attempt = attemptRepository
                .findByStripePaymentID(stripePaymentID)
                .orElseThrow(() -> new PaymentNotFoundException("Payment attempt not found for stripePaymentID: " + stripePaymentID));

        attempt.setStatus(status);
        attempt.setProviderEventId(stripeEventID);
        final PaymentEntity saved = attemptRepository.save(attempt);

        log.info("Payment updated: stripePaymentID={}, status={}", stripePaymentID, status);
        return saved;
    }

    /**
     * Persists the {@link ProcessedWebhookEventEntity} ledger row for the given Stripe event id.
     * Wrapped in the calling transaction so that the dedup row commits atomically with the
     * side-effects above it.
     *
     * @param stripeEventId the {@code event.getId()} returned by the Stripe SDK
     */
    private void recordEventAsProcessed(final String stripeEventId) {

        final ProcessedWebhookEventEntity ledgerRow = ProcessedWebhookEventEntity.builder()
                .stripeEventId(stripeEventId)
                .processedAt(LocalDateTime.now())
                .build();
        processedWebhookEventRepository.save(ledgerRow);
    }

    /**
     * Resolves the order UUID for {@link OrderPaidEvent} publishing. The PaymentEntity has its own
     * {@code orderEntity} association, but we prefer the metadata-supplied UUID for symmetry with
     * {@code OrderPaymentConfirmationEventListener#handlePaymentSuccess}, falling back to the
     * association if the metadata is missing.
     *
     * @param payment the (already-flipped) PaymentEntity
     * @param dto     the parsed webhook DTO
     * @return the resolved UUID, or {@code null} if neither source provides one
     */
    private UUID resolveOrderUuid(final PaymentEntity payment, final StripeWebhookEventDto dto) {

        if (dto != null && dto.getData() != null && dto.getData().getPaymentIntentPayload() != null
                && dto.getData().getPaymentIntentPayload().getMetadata() != null) {
            final String metaUuid = dto.getData().getPaymentIntentPayload().getMetadata().get(ORDER_UUID_METADATA_KEY);
            if (metaUuid != null && !metaUuid.isBlank()) {
                try {
                    return UUID.fromString(metaUuid);
                } catch (final IllegalArgumentException ex) {
                    log.warn("Webhook metadata.order_uuid is not a valid UUID: {}", metaUuid);
                }
            }
        }
        if (payment != null && payment.getOrderEntity() != null) {
            return payment.getOrderEntity().getUuid();
        }
        return null;
    }
}
