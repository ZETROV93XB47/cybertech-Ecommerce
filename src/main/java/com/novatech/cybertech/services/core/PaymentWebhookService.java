package com.novatech.cybertech.services.core;

import com.stripe.model.Event;

/**
 * Service contract for processing Stripe webhook events.
 *
 * <p>Implementations are responsible for:
 * <ul>
 *   <li>Idempotency: skipping events whose {@code event.getId()} has already been processed
 *       (BUG-170 dedup ledger).</li>
 *   <li>Environment safety: skipping events whose {@code livemode} flag does not match the
 *       deployment's expected mode (BUG-522).</li>
 *   <li>Dispatch: delegating to per-event-type handlers (success / failure / refund).</li>
 *   <li>Side-effects: updating the {@code PaymentEntity} row and publishing the matching
 *       Spring application event.</li>
 * </ul>
 */
public interface PaymentWebhookService {

    /**
     * Process a Stripe webhook event end-to-end.
     *
     * <p>The {@code eventPayload} (the raw HTTP body) is forwarded so that the service can
     * deserialize the rich, application-specific {@code StripeWebhookEventDto} (which carries the
     * {@code metadata.order_uuid} read by downstream listeners) without rebuilding the JSON from
     * the typed Stripe SDK objects.
     *
     * @param event        the parsed Stripe {@link Event} produced by signature verification
     * @param eventPayload the raw HTTP body — must be byte-for-byte identical to what Stripe HMACed
     */
    void handleEvent(Event event, String eventPayload);
}
