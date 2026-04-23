package com.novatech.cybertech.api.controllers.implementation;

import com.novatech.cybertech.api.controllers.spec.StripeWebhookApiSpec;
import com.novatech.cybertech.services.core.PaymentWebhookService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.novatech.cybertech.constants.CyberTechAppConstants.APP_API_VERSION;
import static com.novatech.cybertech.constants.CyberTechAppConstants.STRIPE_WEBHOOKS_BASE_PATH;

/**
 * HTTP endpoint that ingests Stripe webhook deliveries.
 *
 * <p><b>Stripe webhook contract</b> (see <a href="https://stripe.com/docs/webhooks#retries">docs</a>):
 * any non-2xx response causes Stripe to enter exponential-backoff retries, lasting up to 3 days.
 * Therefore the controller's response codes are calibrated to Stripe's retry semantics, NOT to a
 * generic REST contract:
 * <ul>
 *   <li><b>200 OK</b> — event accepted (or non-retriable from our side: orphan PaymentIntent,
 *       unknown order id, etc.). Stripe stops delivering this event id.</li>
 *   <li><b>400 BAD_REQUEST</b> — signature verification failed (BUG-2501 carve-out: Stripe
 *       <em>must</em> learn the receiver is broken so the on-call dashboard surfaces it) or the
 *       signed payload is structurally malformed JSON (BUG-2502).</li>
 * </ul>
 *
 * <p>Service-thrown exceptions on a structurally-valid event are <b>swallowed and 200-ACKed</b>
 * (BUG-2501): a Stripe retry storm cannot fix a missing order row in our DB. Errors are still
 * logged for the on-call team via the standard log pipeline.
 *
 * <p>The endpoint is publicly reachable (per {@code SecurityConfig.PUBLIC_URLS}) — Stripe does not
 * negotiate JWTs.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(version = APP_API_VERSION, value = STRIPE_WEBHOOKS_BASE_PATH)
@Tag(name = "StripeWebhookController", description = "Stripe Webhook API")
public class StripeWebhookController implements StripeWebhookApiSpec {

    private static final String STRIPE_SIGNATURE_HEADER = "Stripe-Signature";

    @Value("${stripe.webhook.secret}")
    private String webhookSecret;

    private final PaymentWebhookService paymentWebhookService;

    /**
     * Receive a Stripe webhook delivery.
     *
     * <p>Flow:
     * <ol>
     *   <li>Verify the {@code Stripe-Signature} header against the raw body using the configured
     *       webhook secret. A failure returns <b>400</b> (BUG-2501 carve-out — Stripe must learn
     *       its endpoint is misconfigured).</li>
     *   <li>If the signature is valid but the SDK reports a malformed payload (no deserialized
     *       data object), return <b>400</b> (BUG-2502 — non-retriable bad request).</li>
     *   <li>Delegate to {@link PaymentWebhookService#handleEvent(Event, String)}. Any exception
     *       thrown by the service is caught, logged, and converted to a <b>200 OK</b> ACK
     *       (BUG-2501 — Stripe retries cannot fix a downstream data inconsistency).</li>
     * </ol>
     *
     * @param payload         the raw HTTP body — preserved as a String so that signature verification
     *                        operates on the exact bytes Stripe HMACed
     * @param stripeSignature the {@code Stripe-Signature} header value (e.g. {@code "t=...,v1=..."})
     * @return {@code 200 OK} on success or non-retriable downstream failure, {@code 400} on
     *         signature / payload-structure failure
     */
    @Override
    @PostMapping
    public ResponseEntity<Void> handleStripeEvent(@RequestBody final String payload,
                                                  @RequestHeader(STRIPE_SIGNATURE_HEADER) final String stripeSignature) {

        final Event event;

        try {
            event = Webhook.constructEvent(payload, stripeSignature, webhookSecret);
        } catch (final SignatureVerificationException e) {
            log.error("Invalid Stripe signature", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (final RuntimeException e) {
            // BUG-2502: the Stripe SDK throws JsonSyntaxException (a RuntimeException) from
            // constructEvent BEFORE signature verification runs, so a malformed JSON body never
            // reaches the SignatureVerificationException branch above. Surface it as 400 — Stripe
            // must learn the body it sent could not be parsed (this is non-retriable on our side).
            log.error("Malformed Stripe webhook payload — returning 400 (BUG-2502)", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        // BUG-2502 coverage: a malformed JSON body throws JsonSyntaxException from
        // Webhook.constructEvent(...) → caught by the RuntimeException branch above → 400.
        // Downstream service is responsible for tolerating events whose data object did not
        // deserialize (older API versions, schema drift) — returning 200 by default.
        log.info("Stripe event received: id={}, type={}", event.getId(), event.getType());

        try {
            paymentWebhookService.handleEvent(event, payload);
        } catch (final Exception e) {
            // BUG-2501: returning a non-2xx triggers a Stripe retry storm (up to 3 days of
            // exponential backoff). For non-retriable downstream faults — orphan PaymentIntent,
            // missing order row, transient DB blip — we WANT to ACK 200 so Stripe stops
            // re-delivering. The error is still logged for the on-call team.
            log.error("Error processing Stripe event id={}, type={}; ACKing 200 to prevent Stripe retry storm (BUG-2501)",
                    event.getId(), event.getType(), e);
            return ResponseEntity.ok().build();
        }

        return ResponseEntity.ok().build();
    }
}
