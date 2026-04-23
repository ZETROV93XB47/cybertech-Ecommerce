package com.novatech.cybertech.api.controllers.implementation.stripewebhook;

import com.novatech.cybertech.api.controllers.TestSecurityConfig;
import com.novatech.cybertech.api.controllers.implementation.StripeWebhookController;
import com.novatech.cybertech.exceptions.PaymentNotFoundException;
import com.novatech.cybertech.fixtures.support.stubs.StripeEventBuilder;
import com.novatech.cybertech.services.core.PaymentWebhookService;
import com.stripe.model.Event;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice tests for {@link StripeWebhookController}. The webhook is anonymously reachable in
 * production (per {@code SecurityConfig.PUBLIC_URLS}) and SA-W0 mirrored that into the shared
 * {@link TestSecurityConfig} (entry {@code "/api/v1/webhooks/**"}), so we do NOT need a sibling
 * security config — just import the canonical one and POST without a JWT.
 *
 * <p>The controller catches only {@link com.stripe.exception.SignatureVerificationException} and
 * forwards everything else to the {@link PaymentWebhookService}, which means malformed JSON
 * (which throws {@code JsonSyntaxException} from {@code Webhook.constructEvent} BEFORE signature
 * verification runs) and any service-thrown exception leak out as 500 — pinned via BUG-2501 /
 * BUG-2502 below.
 */
@Slf4j
@Import({TestSecurityConfig.class})
@WebMvcTest(value = StripeWebhookController.class)
@TestPropertySource(properties = "stripe.webhook.secret=" + StripeWebhookControllerTest.WEBHOOK_SECRET)
class StripeWebhookControllerTest {

    static final String WEBHOOK_SECRET = "whsec_test_sa_w23_secret";

    private static final String STRIPE_WEBHOOK_ENDPOINT = "/api/v1/webhooks/stripe";
    private static final String STRIPE_SIGNATURE_HEADER = "Stripe-Signature";

    private static final String VALID_EVENT_JSON = """
            {
              "id": "evt_test_123",
              "object": "event",
              "api_version": "2024-04-10",
              "created": 1700000000,
              "type": "payment_intent.succeeded",
              "data": {
                "object": {
                  "id": "pi_test_123",
                  "object": "payment_intent",
                  "amount": 10000,
                  "currency": "eur",
                  "status": "succeeded"
                }
              },
              "livemode": false,
              "pending_webhooks": 0,
              "request": {"id": null, "idempotency_key": null}
            }
            """;

    private static final String UNKNOWN_TYPE_EVENT_JSON = """
            {
              "id": "evt_unknown_999",
              "object": "event",
              "api_version": "2024-04-10",
              "created": 1700000000,
              "type": "customer.subscription.updated",
              "data": {"object": {"id": "sub_xyz", "object": "subscription"}},
              "livemode": false,
              "pending_webhooks": 0,
              "request": {"id": null, "idempotency_key": null}
            }
            """;

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    PaymentWebhookService paymentWebhookService;

    // ----- Happy path --------------------------------------------------------------------

    @Test
    void shouldReturn200WhenSignatureValidAndServiceAcceptsEvent() throws Exception {
        StripeEventBuilder.Signed signed = StripeEventBuilder.signedPayloadNow(WEBHOOK_SECRET, VALID_EVENT_JSON);
        doNothing().when(paymentWebhookService).handleEvent(any(Event.class), anyString());

        mockMvc.perform(post(STRIPE_WEBHOOK_ENDPOINT)
                        .header(STRIPE_SIGNATURE_HEADER, signed.header())
                        .contentType(APPLICATION_JSON)
                        .content(signed.payload()))
                .andExpect(status().isOk());

        verify(paymentWebhookService).handleEvent(any(Event.class), eq(signed.payload()));
    }

    // ----- Anonymous reachability ---------------------------------------------------------

    /**
     * Confirms the production {@code permitAll} on {@code /api/v1/webhooks/**} is mirrored in
     * {@link TestSecurityConfig}: a POST with NO JWT and a tampered signature returns 400 (the
     * controller's own signature-rejection branch), proving Spring Security did not short-circuit
     * with a 401/403 first.
     */
    @Test
    void webhookEndpointShouldBePublicAndNotRequireJwt() throws Exception {
        mockMvc.perform(post(STRIPE_WEBHOOK_ENDPOINT)
                        .header(STRIPE_SIGNATURE_HEADER, "t=1700000000,v1=tampered_hash_value_deadbeef")
                        .contentType(APPLICATION_JSON)
                        .content(VALID_EVENT_JSON))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(paymentWebhookService);
    }

    // ----- Raw body preservation ----------------------------------------------------------

    /**
     * If anyone later switches the controller from {@code @RequestBody String payload} to a parsed
     * object, signature verification breaks because Stripe HMACs the EXACT bytes. Capture the
     * payload string passed to the service and assert it equals the wire-bytes — including
     * non-canonical whitespace and key-order — that we signed.
     */
    @Test
    void shouldPreserveRawBodyForSignatureVerification() throws Exception {
        // Payload with unusual whitespace and reversed key-order vs the "valid" sample. Each byte
        // matters for HMAC.
        String wirePayload = "{   \"livemode\":false,\"type\":\"payment_intent.succeeded\","
                + "\"id\":\"evt_raw_body_777\",\"object\":\"event\",\"api_version\":\"2024-04-10\","
                + "\"created\":1700000000,\"pending_webhooks\":0,"
                + "\"request\":{\"id\":null,\"idempotency_key\":null},"
                + "\"data\":{\"object\":{\"id\":\"pi_raw_body_777\",\"object\":\"payment_intent\","
                + "\"amount\":4242,\"currency\":\"eur\",\"status\":\"succeeded\"}}}";
        StripeEventBuilder.Signed signed = StripeEventBuilder.signedPayloadNow(WEBHOOK_SECRET, wirePayload);

        mockMvc.perform(post(STRIPE_WEBHOOK_ENDPOINT)
                        .header(STRIPE_SIGNATURE_HEADER, signed.header())
                        .contentType(APPLICATION_JSON)
                        .content(signed.payload()))
                .andExpect(status().isOk());

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(paymentWebhookService).handleEvent(any(Event.class), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue()).isEqualTo(wirePayload);
    }

    // ----- Tampered v1= hash --------------------------------------------------------------

    @Test
    void shouldRejectTamperedV1HashWith400() throws Exception {
        StripeEventBuilder.Signed signed = StripeEventBuilder.signedPayloadNow(WEBHOOK_SECRET, VALID_EVENT_JSON);
        // Flip the last hex char of v1=
        String tampered = signed.header().substring(0, signed.header().length() - 1)
                + (signed.header().endsWith("a") ? "b" : "a");

        mockMvc.perform(post(STRIPE_WEBHOOK_ENDPOINT)
                        .header(STRIPE_SIGNATURE_HEADER, tampered)
                        .contentType(APPLICATION_JSON)
                        .content(signed.payload()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(paymentWebhookService);
    }

    // ----- Wrong secret -------------------------------------------------------------------

    @Test
    void shouldRejectSignatureFromWrongSecretWith400() throws Exception {
        StripeEventBuilder.Signed signed = StripeEventBuilder.signedPayloadNow("whsec_a_DIFFERENT_secret", VALID_EVENT_JSON);

        mockMvc.perform(post(STRIPE_WEBHOOK_ENDPOINT)
                        .header(STRIPE_SIGNATURE_HEADER, signed.header())
                        .contentType(APPLICATION_JSON)
                        .content(signed.payload()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(paymentWebhookService);
    }

    // ----- Malformed header string --------------------------------------------------------

    @Test
    void shouldRejectMalformedSignatureHeaderWith400() throws Exception {
        mockMvc.perform(post(STRIPE_WEBHOOK_ENDPOINT)
                        .header(STRIPE_SIGNATURE_HEADER, "this-is-not-a-valid-stripe-signature-header")
                        .contentType(APPLICATION_JSON)
                        .content(VALID_EVENT_JSON))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(paymentWebhookService);
    }

    // ----- Missing header → MissingRequestHeaderException → 400 --------------------------

    @Test
    void shouldReturn400WhenStripeSignatureHeaderMissing() throws Exception {
        mockMvc.perform(post(STRIPE_WEBHOOK_ENDPOINT)
                        .contentType(APPLICATION_JSON)
                        .content(VALID_EVENT_JSON))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(paymentWebhookService);
    }

    // ----- Unknown event type — controller delegates unconditionally ---------------------

    @Test
    void shouldAckUnknownEventTypeWithoutSpecialHandling() throws Exception {
        StripeEventBuilder.Signed signed = StripeEventBuilder.signedPayloadNow(WEBHOOK_SECRET, UNKNOWN_TYPE_EVENT_JSON);
        doNothing().when(paymentWebhookService).handleEvent(any(Event.class), anyString());

        mockMvc.perform(post(STRIPE_WEBHOOK_ENDPOINT)
                        .header(STRIPE_SIGNATURE_HEADER, signed.header())
                        .contentType(APPLICATION_JSON)
                        .content(signed.payload()))
                .andExpect(status().isOk());

        // Controller forwards every successfully-verified event; service handles "unknown" branch.
        verify(paymentWebhookService).handleEvent(any(Event.class), eq(signed.payload()));
    }

    // ----- Idempotency NOT enforced at controller — pin replay forwarding ---------------

    /**
     * F1.4 claimed to add a {@code ProcessedWebhookEventEntity} for service-side dedup, but the
     * entity is NOT in the production tree (verified by file search). Even once it lands, the
     * CONTROLLER forwards every signature-valid event — dedup is the service's job. Pin the
     * controller's stateless behaviour so a future regression that adds short-circuit caching at
     * the controller layer is caught.
     */
    @Test
    void shouldForwardReplayedEventToServiceEachTime() throws Exception {
        StripeEventBuilder.Signed signed = StripeEventBuilder.signedPayloadNow(WEBHOOK_SECRET, VALID_EVENT_JSON);
        doNothing().when(paymentWebhookService).handleEvent(any(Event.class), anyString());

        // Same signed payload sent twice
        mockMvc.perform(post(STRIPE_WEBHOOK_ENDPOINT)
                        .header(STRIPE_SIGNATURE_HEADER, signed.header())
                        .contentType(APPLICATION_JSON)
                        .content(signed.payload()))
                .andExpect(status().isOk());

        mockMvc.perform(post(STRIPE_WEBHOOK_ENDPOINT)
                        .header(STRIPE_SIGNATURE_HEADER, signed.header())
                        .contentType(APPLICATION_JSON)
                        .content(signed.payload()))
                .andExpect(status().isOk());

        verify(paymentWebhookService, org.mockito.Mockito.times(2))
                .handleEvent(any(Event.class), eq(signed.payload()));
    }

    // ----- HTTP method gating: GET / PUT → 405 ------------------------------------------

    @Test
    void shouldReturn405ForGetOnWebhookPath() throws Exception {
        mockMvc.perform(get(STRIPE_WEBHOOK_ENDPOINT))
                .andExpect(status().isMethodNotAllowed());

        verifyNoInteractions(paymentWebhookService);
    }

    @Test
    void shouldReturn405ForPutOnWebhookPath() throws Exception {
        mockMvc.perform(put(STRIPE_WEBHOOK_ENDPOINT)
                        .contentType(APPLICATION_JSON)
                        .content(VALID_EVENT_JSON))
                .andExpect(status().isMethodNotAllowed());

        verifyNoInteractions(paymentWebhookService);
    }

    // ----- BUG-2501: orphan PaymentIntent — service throws → 500 (still broken) ---------

    /**
     * F1.4 claimed BUG-2501 closed by adding {@code ProcessedWebhookEventEntity} dedup + 200-ACK on
     * non-retriable. Direct file search confirms that entity does NOT exist in {@code src/main}, and
     * {@link StripeWebhookController#handleStripeEvent} still has no try/catch around
     * {@code paymentWebhookService.handleEvent(...)}. So a service-thrown
     * {@link PaymentNotFoundException} surfaces through the {@code @ExceptionHandler} chain — F1.1
     * registered a 404 handler for it, so today the response is 404 NOT_FOUND. Stripe will retry
     * any non-2xx response, including 404. Pin BOTH:
     * <ul>
     *   <li>Current behaviour: 404 surfaces (would be 500 pre-F1.1, is 404 post-F1.1 — Stripe will
     *       still retry).</li>
     *   <li>Desired behaviour: 200-ACK on non-retriable faults — disabled.</li>
     * </ul>
     */
    @Test
    void shouldNotAck200WhenServiceThrowsForOrphanPaymentIntent_BUG_2501_currentBehaviour() throws Exception {
        StripeEventBuilder.Signed signed = StripeEventBuilder.signedPayloadNow(WEBHOOK_SECRET, VALID_EVENT_JSON);
        doThrow(new PaymentNotFoundException("Payment attempt not found for stripePaymentID: pi_test_123"))
                .when(paymentWebhookService).handleEvent(any(Event.class), anyString());

        // Controller has no try/catch; PaymentNotFoundException → 404 via @ControllerAdvice.
        // Stripe will retry any non-2xx response — this is the BUG-2501 leak vector.
        mockMvc.perform(post(STRIPE_WEBHOOK_ENDPOINT)
                        .header(STRIPE_SIGNATURE_HEADER, signed.header())
                        .contentType(APPLICATION_JSON)
                        .content(signed.payload()))
                .andExpect(status().isNotFound());
    }

    @Test
    @Disabled("BUG-2501 — controller should ACK 200 on non-retriable service failures (e.g., orphan " +
            "PaymentIntent / unknown order id). Today any service exception leaks as a non-2xx, " +
            "causing Stripe to retry. Flip green when controller wraps service.handleEvent in a " +
            "try/catch that 200-ACKs unrecoverable cases.")
    void shouldAck200WhenServiceThrowsForOrphanPaymentIntent_BUG_2501_desired() throws Exception {
        StripeEventBuilder.Signed signed = StripeEventBuilder.signedPayloadNow(WEBHOOK_SECRET, VALID_EVENT_JSON);
        doThrow(new PaymentNotFoundException("Payment attempt not found for stripePaymentID: pi_test_123"))
                .when(paymentWebhookService).handleEvent(any(Event.class), anyString());

        mockMvc.perform(post(STRIPE_WEBHOOK_ENDPOINT)
                        .header(STRIPE_SIGNATURE_HEADER, signed.header())
                        .contentType(APPLICATION_JSON)
                        .content(signed.payload()))
                .andExpect(status().isOk());
    }

    // ----- BUG-2502: signed-but-malformed-JSON → 500 (still broken) ----------------------

    /**
     * {@code Webhook.constructEvent} deserializes the payload via {@code StripeObject.deserializeStripeObject}
     * BEFORE running signature verification. A malformed JSON body therefore throws a
     * {@code JsonSyntaxException} (RuntimeException) — NOT a
     * {@link com.stripe.exception.SignatureVerificationException} — and the controller has no catch
     * for it. The {@code @ExceptionHandler(RuntimeException.class)} catch-all wraps it as 500
     * APPLICATION_ERROR.
     *
     * <p>Pin BOTH the current 500 leak and the desired 400 — F1 has not addressed this surface.
     */
    @Test
    void shouldReturn500WhenSignedPayloadIsMalformedJson_BUG_2502_currentBehaviour() throws Exception {
        String malformed = "{\"id\":\"evt_test\",\"this is not valid JSON";
        StripeEventBuilder.Signed signed = StripeEventBuilder.signedPayloadNow(WEBHOOK_SECRET, malformed);

        mockMvc.perform(post(STRIPE_WEBHOOK_ENDPOINT)
                        .header(STRIPE_SIGNATURE_HEADER, signed.header())
                        .contentType(APPLICATION_JSON)
                        .content(signed.payload()))
                .andExpect(status().isInternalServerError());

        verifyNoInteractions(paymentWebhookService);
    }

    @Test
    @Disabled("BUG-2502 — signed-but-malformed-JSON should return 400 BAD_REQUEST. Today the Stripe " +
            "SDK throws JsonSyntaxException from constructEvent BEFORE signature verification, " +
            "the controller does not catch it, and the global advice wraps it as 500. Flip green " +
            "when the controller catches RuntimeException from constructEvent and returns 400.")
    void shouldReturn400WhenSignedPayloadIsMalformedJson_BUG_2502_desired() throws Exception {
        String malformed = "{\"id\":\"evt_test\",\"this is not valid JSON";
        StripeEventBuilder.Signed signed = StripeEventBuilder.signedPayloadNow(WEBHOOK_SECRET, malformed);

        mockMvc.perform(post(STRIPE_WEBHOOK_ENDPOINT)
                        .header(STRIPE_SIGNATURE_HEADER, signed.header())
                        .contentType(APPLICATION_JSON)
                        .content(signed.payload()))
                .andExpect(status().isBadRequest());
    }
}
