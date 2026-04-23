package com.novatech.cybertech.integration.payment;

import com.novatech.cybertech.TestcontainersConfiguration;
import com.novatech.cybertech.entities.OrderEntity;
import com.novatech.cybertech.entities.PaymentEntity;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.entities.enums.OrderStatus;
import com.novatech.cybertech.entities.enums.PaymentAttemptStatus;
import com.novatech.cybertech.events.OrderPaidEvent;
import com.novatech.cybertech.events.PaymentFailedEvent;
import com.novatech.cybertech.events.PaymentRefundedEvent;
import com.novatech.cybertech.events.PaymentSucceededEvent;
import com.novatech.cybertech.fixtures.builders.OrderEntityBuilder;
import com.novatech.cybertech.fixtures.builders.PaymentEntityBuilder;
import com.novatech.cybertech.fixtures.builders.UserEntityBuilder;
import com.novatech.cybertech.fixtures.support.stubs.StripeEventBuilder;
import com.novatech.cybertech.repositories.OrderRepository;
import com.novatech.cybertech.repositories.PaymentAttemptRepository;
import com.novatech.cybertech.repositories.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SA-W5.3 — End-to-end Stripe webhook flow IT against the real Wave-1 Testcontainers stack
 * (MySQL + Redis + Elasticsearch + MongoDB) wired through the W0 public
 * {@link TestcontainersConfiguration}.
 *
 * <p>The endpoint {@code POST /api/v1/webhooks/stripe} is publicly reachable per
 * {@code SecurityConfig.PUBLIC_URLS} (and mirrored in {@code TestSecurityConfig} by W0 via
 * {@code "/api/v1/webhooks/**"}). All requests are signed with the test secret using the
 * shared {@link StripeEventBuilder} fixture.
 *
 * <p><b>SA-Fix-3 closures verified end-to-end here:</b>
 * <ul>
 *   <li><b>BUG-170</b> — Dedup ledger ({@code ProcessedWebhookEventEntity}) short-circuits replays.</li>
 *   <li><b>BUG-171</b> — {@link OrderPaidEvent} published from {@code handlePaymentSucceeded}.</li>
 *   <li><b>BUG-520</b> — {@link PaymentFailedEvent} published from {@code handlePaymentFailed}.</li>
 *   <li><b>BUG-521</b> — Terminal-state guard prevents SUCCESS→FAILED row regression.</li>
 *   <li><b>BUG-522</b> — Livemode mismatch silently drops the event.</li>
 *   <li><b>BUG-2501</b> — Controller 200-ACKs non-retriable downstream failures.</li>
 *   <li><b>BUG-2502</b> — Controller returns 400 on signed-but-malformed JSON.</li>
 *   <li><b>BUG-172</b> — {@code metadata.order_uuid} fixture key matches the listener.</li>
 * </ul>
 */
@Slf4j
@Testcontainers
@AutoConfigureMockMvc
@ActiveProfiles("test")
@RecordApplicationEvents
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        // Production reads `stripe.webhook.secret` (with a dot) — matches the slice test as well.
        // The brief mentioned `stripe.webhook-secret`; using the actual production key is required.
        "stripe.webhook.secret=" + PaymentWebhookFlowIT.WEBHOOK_SECRET
})
class PaymentWebhookFlowIT {

    static final String WEBHOOK_SECRET = "whsec_test_sa_w53_secret";

    private static final String STRIPE_WEBHOOK_ENDPOINT = "/api/v1/webhooks/stripe";
    private static final String STRIPE_SIGNATURE_HEADER = "Stripe-Signature";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentAttemptRepository paymentAttemptRepository;

    @Autowired
    private ApplicationEvents applicationEvents;

    private UUID orderUuid;
    private String stripePaymentId;
    private String idempotencyKey;

    @BeforeEach
    void seed() {
        // Distinct identifiers per test for isolation (no @Transactional rollback because
        // the webhook path opens its own transactions on AFTER_COMMIT listener side).
        stripePaymentId = "pi_test_" + UUID.randomUUID().toString().replace("-", "");
        idempotencyKey = "idem-" + UUID.randomUUID();

        final UserEntity user = UserEntityBuilder.aValidUserBuilder()
                .email("user-" + UUID.randomUUID() + "@example.com")
                .keycloakId("kc-" + UUID.randomUUID())
                .build();
        userRepository.save(user);

        final OrderEntity order = OrderEntityBuilder.aValidOrderBuilder()
                .userEntity(user)
                .status(OrderStatus.AWAITING_PAYMENT)
                .build();
        orderRepository.save(order);
        orderUuid = order.getUuid();

        // Seed the PaymentEntity that the webhook will look up by stripePaymentID. PaymentAttemptStatus
        // does not declare a PENDING constant — the closest non-terminal value is PROCESSING.
        final PaymentEntity payment = PaymentEntityBuilder.aValidPaymentBuilder()
                .orderEntity(order)
                .stripePaymentID(stripePaymentId)
                .status(PaymentAttemptStatus.PROCESSING)
                .idempotencyKey(idempotencyKey)
                .build();
        paymentAttemptRepository.save(payment);
    }

    // ---------------------------------------------------------------------------------
    // 1) Valid-signature happy path — service ACKs 200 + PaymentSucceededEvent published
    // ---------------------------------------------------------------------------------

    /**
     * The webhook accepts a signed {@code payment_intent.succeeded} event, the controller ACKs
     * 200, and the service publishes {@link PaymentSucceededEvent} so the
     * {@code OrderPaymentConfirmationEventListener} (AFTER_COMMIT, async) can flip the order.
     *
     * <p>Note: BUG-171 (no {@link OrderPaidEvent} published) is asserted separately below — this
     * test only verifies the existing {@link PaymentSucceededEvent} surface.
     */
    @Test
    void validSignatureHappyPathReturns200AndPublishesPaymentSucceededEvent() throws Exception {
        final String payload = paymentIntentSucceededJson(stripePaymentId, orderUuid, idempotencyKey);
        final StripeEventBuilder.Signed signed = StripeEventBuilder.signedPayloadNow(WEBHOOK_SECRET, payload);

        mockMvc.perform(post(STRIPE_WEBHOOK_ENDPOINT)
                        .header(STRIPE_SIGNATURE_HEADER, signed.header())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signed.payload()))
                .andExpect(status().isOk());

        // The controller's transaction commits before returning; the event is published synchronously
        // by the service, so it must be observable on the recorder immediately.
        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(applicationEvents.stream(PaymentSucceededEvent.class).count())
                        .as("exactly one PaymentSucceededEvent should be published on a valid SUCCESS webhook")
                        .isEqualTo(1L)
        );

        // Payment row updated in DB
        final PaymentEntity reloaded = paymentAttemptRepository.findByStripePaymentID(stripePaymentId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(PaymentAttemptStatus.SUCCESS);
        assertThat(reloaded.getProviderEventId()).isNotBlank();
    }

    // ---------------------------------------------------------------------------------
    // 2) Replay / idempotency — BUG-170 still open (ProcessedWebhookEventEntity NOT in tree)
    // ---------------------------------------------------------------------------------

    /**
     * BUG-170 fix verification — sending the SAME signed event twice now publishes
     * {@link PaymentSucceededEvent} exactly ONCE. The dedup ledger
     * ({@code ProcessedWebhookEventEntity}) records the {@code event.getId()} after the first
     * successful processing; the second delivery short-circuits at the top of
     * {@code handleEvent}.
     */
    @Test
    void replayOfSameEventShouldPublishOnlyOnce_BUG_170() throws Exception {
        final String payload = paymentIntentSucceededJson(stripePaymentId, orderUuid, idempotencyKey);
        final StripeEventBuilder.Signed signed = StripeEventBuilder.signedPayloadNow(WEBHOOK_SECRET, payload);

        mockMvc.perform(post(STRIPE_WEBHOOK_ENDPOINT)
                        .header(STRIPE_SIGNATURE_HEADER, signed.header())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signed.payload()))
                .andExpect(status().isOk());
        mockMvc.perform(post(STRIPE_WEBHOOK_ENDPOINT)
                        .header(STRIPE_SIGNATURE_HEADER, signed.header())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signed.payload()))
                .andExpect(status().isOk());

        assertThat(applicationEvents.stream(PaymentSucceededEvent.class).count()).isEqualTo(1L);
    }

    // ---------------------------------------------------------------------------------
    // 3) Tampered signature → 400
    // ---------------------------------------------------------------------------------

    @Test
    void tamperedSignatureReturns400() throws Exception {
        final String payload = paymentIntentSucceededJson(stripePaymentId, orderUuid, idempotencyKey);
        final StripeEventBuilder.Signed signed = StripeEventBuilder.signedPayloadNow(WEBHOOK_SECRET, payload);

        // Flip the last hex char of the v1= digest
        final String tamperedHeader = signed.header().substring(0, signed.header().length() - 1)
                + (signed.header().endsWith("a") ? "b" : "a");

        mockMvc.perform(post(STRIPE_WEBHOOK_ENDPOINT)
                        .header(STRIPE_SIGNATURE_HEADER, tamperedHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signed.payload()))
                .andExpect(status().isBadRequest());

        // No event should be published because the service is never invoked.
        assertThat(applicationEvents.stream(PaymentSucceededEvent.class).count()).isZero();

        // Payment row untouched
        final PaymentEntity reloaded = paymentAttemptRepository.findByStripePaymentID(stripePaymentId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(PaymentAttemptStatus.PROCESSING);
    }

    // ---------------------------------------------------------------------------------
    // 4) Malformed JSON with valid signature — BUG-2502 (still broken per F1)
    // ---------------------------------------------------------------------------------

    /**
     * BUG-2502 fix verification — a valid HMAC over a malformed JSON body now returns 400. The
     * controller catches the {@code JsonSyntaxException} (RuntimeException) thrown by
     * {@code constructEvent} before signature verification.
     */
    @Test
    void malformedJsonWithValidSignatureShouldReturn400_BUG_2502() throws Exception {
        final String malformed = "{\"id\":\"evt_test\",\"this is not valid JSON";
        final StripeEventBuilder.Signed signed = StripeEventBuilder.signedPayloadNow(WEBHOOK_SECRET, malformed);

        mockMvc.perform(post(STRIPE_WEBHOOK_ENDPOINT)
                        .header(STRIPE_SIGNATURE_HEADER, signed.header())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signed.payload()))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------------------------
    // 5) payment_intent.payment_failed — BUG-520 (NEW): no PaymentFailedEvent is published
    // ---------------------------------------------------------------------------------

    /**
     * BUG-520 fix verification — {@code handlePaymentFailed} now publishes
     * {@link PaymentFailedEvent} after flipping the payment row to FAILED. The order-confirmation
     * listener can therefore release the held stock and flip the order to {@code PAYMENT_FAILED}.
     */
    @Test
    void paymentIntentFailedShouldPublishFailedEvent_BUG_520() throws Exception {
        final String payload = paymentIntentFailedJson(stripePaymentId, orderUuid, idempotencyKey);
        final StripeEventBuilder.Signed signed = StripeEventBuilder.signedPayloadNow(WEBHOOK_SECRET, payload);

        mockMvc.perform(post(STRIPE_WEBHOOK_ENDPOINT)
                        .header(STRIPE_SIGNATURE_HEADER, signed.header())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signed.payload()))
                .andExpect(status().isOk());

        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(applicationEvents.stream(PaymentFailedEvent.class).count()).isEqualTo(1L)
        );
    }

    // ---------------------------------------------------------------------------------
    // 6) Out-of-order regression — SUCCESS then FAILED for same payment intent
    // ---------------------------------------------------------------------------------

    /**
     * BUG-521 fix verification — once a payment row is in {@link PaymentAttemptStatus#SUCCESS},
     * a subsequent {@code payment_intent.payment_failed} (out-of-order Stripe delivery) is
     * dropped. The terminal-state guard in {@code handlePaymentFailed} short-circuits the row
     * regression.
     */
    @Test
    void outOfOrderSuccessThenFailedShouldKeepSuccessTerminal_BUG_521() throws Exception {
        final String successPayload = paymentIntentSucceededJson(stripePaymentId, orderUuid, idempotencyKey);
        final StripeEventBuilder.Signed signed1 = StripeEventBuilder.signedPayloadNow(WEBHOOK_SECRET, successPayload);
        mockMvc.perform(post(STRIPE_WEBHOOK_ENDPOINT)
                        .header(STRIPE_SIGNATURE_HEADER, signed1.header())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signed1.payload()))
                .andExpect(status().isOk());

        final String failedPayload = paymentIntentFailedJson(stripePaymentId, orderUuid, idempotencyKey);
        final StripeEventBuilder.Signed signed2 = StripeEventBuilder.signedPayloadNow(WEBHOOK_SECRET, failedPayload);
        mockMvc.perform(post(STRIPE_WEBHOOK_ENDPOINT)
                        .header(STRIPE_SIGNATURE_HEADER, signed2.header())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signed2.payload()))
                .andExpect(status().isOk());

        assertThat(paymentAttemptRepository.findByStripePaymentID(stripePaymentId).orElseThrow().getStatus())
                .isEqualTo(PaymentAttemptStatus.SUCCESS);
    }

    // ---------------------------------------------------------------------------------
    // 7) Orphan PaymentIntent — BUG-2501 still open (controller has no try/catch)
    // ---------------------------------------------------------------------------------

    /**
     * BUG-2501 fix verification — an orphan {@code pi_*} with no matching payment row no longer
     * leaks as a 404/5xx. The controller catches the {@link com.novatech.cybertech.exceptions.PaymentNotFoundException}
     * (and any other service exception) and ACKs 200, preventing a Stripe retry storm.
     */
    @Test
    void orphanPaymentIntentShouldAck200_BUG_2501() throws Exception {
        final String orphanPi = "pi_orphan_" + UUID.randomUUID().toString().replace("-", "");
        final String payload = paymentIntentSucceededJson(orphanPi, orderUuid, "idem-" + UUID.randomUUID());
        final StripeEventBuilder.Signed signed = StripeEventBuilder.signedPayloadNow(WEBHOOK_SECRET, payload);

        mockMvc.perform(post(STRIPE_WEBHOOK_ENDPOINT)
                        .header(STRIPE_SIGNATURE_HEADER, signed.header())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signed.payload()))
                .andExpect(status().isOk());
    }

    // ---------------------------------------------------------------------------------
    // 8) livemode=true mismatch in a test environment
    // ---------------------------------------------------------------------------------

    /**
     * BUG-522 fix verification — a {@code livemode=true} event delivered to a test deployment
     * (where {@code stripe.livemode=false}) is silently dropped: the controller still 200-ACKs
     * (so Stripe stops re-delivering) but the service short-circuits before any side-effect, so
     * the payment row stays {@link PaymentAttemptStatus#PROCESSING}.
     */
    @Test
    void livemodeTrueShouldBeIgnoredInTestEnvironment_BUG_522() throws Exception {
        final String payload = paymentIntentSucceededJson(stripePaymentId, orderUuid, idempotencyKey)
                .replace("\"livemode\": false", "\"livemode\": true");
        final StripeEventBuilder.Signed signed = StripeEventBuilder.signedPayloadNow(WEBHOOK_SECRET, payload);

        mockMvc.perform(post(STRIPE_WEBHOOK_ENDPOINT)
                        .header(STRIPE_SIGNATURE_HEADER, signed.header())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signed.payload()))
                .andExpect(status().isOk());

        // Service short-circuits → payment row untouched, no domain event published.
        final PaymentEntity reloaded = paymentAttemptRepository.findByStripePaymentID(stripePaymentId).orElseThrow();
        assertThat(reloaded.getStatus())
                .as("BUG-522: livemode mismatch must NOT mutate the payment row")
                .isEqualTo(PaymentAttemptStatus.PROCESSING);
        assertThat(applicationEvents.stream(PaymentSucceededEvent.class).count())
                .as("BUG-522: livemode mismatch must NOT publish PaymentSucceededEvent")
                .isZero();
    }

    // ---------------------------------------------------------------------------------
    // 9) BUG-172 — fixture metadata key matches consumer; happy path flips order to PAID
    // ---------------------------------------------------------------------------------

    /**
     * Direct source-read of {@code OrderPaymentConfirmationEventListener#handlePaymentSuccess} (line
     * 39) confirms the listener reads {@code metadata.get("order_uuid")}. Direct source-read of
     * {@code PaymentDtoFixtures#aValidPaymentIntentPayload} (line 42) confirms the fixture writes
     * {@code metadata.put("order_uuid", ...)}. Keys MATCH today — BUG-172 is closed by W0's wiring.
     *
     * <p>This test signs a payload using the SAME production key, posts it, and waits for the
     * AFTER_COMMIT async listener to flip {@link OrderStatus#AWAITING_PAYMENT} → {@link OrderStatus#PAID}.
     */
    @Test
    void productionMetadataKeyOrderUuidFlipsOrderStatusToPaid_BUG_172() throws Exception {
        final String payload = paymentIntentSucceededJson(stripePaymentId, orderUuid, idempotencyKey);
        // Source-read sanity assertion (would also fail-compile if the JSON shape regressed)
        assertThat(payload).contains("\"order_uuid\": \"" + orderUuid + "\"");

        final StripeEventBuilder.Signed signed = StripeEventBuilder.signedPayloadNow(WEBHOOK_SECRET, payload);

        mockMvc.perform(post(STRIPE_WEBHOOK_ENDPOINT)
                        .header(STRIPE_SIGNATURE_HEADER, signed.header())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signed.payload()))
                .andExpect(status().isOk());

        // Async + AFTER_COMMIT: poll the order status. Stock commit + cart-clear may also run,
        // but only the order status is asserted to keep the test focused on the metadata key.
        Awaitility.await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(200)).untilAsserted(() -> {
            final OrderEntity reloaded = orderRepository.findByUuid(orderUuid).orElseThrow();
            assertThat(reloaded.getStatus())
                    .as("BUG-172: with metadata.order_uuid the listener flips the order to PAID")
                    .isEqualTo(OrderStatus.PAID);
        });
    }

    // ---------------------------------------------------------------------------------
    // 10) BUG-171 — OrderPaidEvent is declared but NEVER published (still open per source read)
    // ---------------------------------------------------------------------------------

    /**
     * BUG-171 fix verification — {@link OrderPaidEvent} is now published from the webhook path
     * after the payment row flips to SUCCESS. The shipping listener can therefore react.
     */
    @Test
    void orderPaidEventShouldBePublishedAfterOrderFlipsToPaid_BUG_171() throws Exception {
        final String payload = paymentIntentSucceededJson(stripePaymentId, orderUuid, idempotencyKey);
        final StripeEventBuilder.Signed signed = StripeEventBuilder.signedPayloadNow(WEBHOOK_SECRET, payload);

        mockMvc.perform(post(STRIPE_WEBHOOK_ENDPOINT)
                        .header(STRIPE_SIGNATURE_HEADER, signed.header())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signed.payload()))
                .andExpect(status().isOk());

        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(applicationEvents.stream(OrderPaidEvent.class).count()).isEqualTo(1L)
        );
    }

    // ---------------------------------------------------------------------------------
    // 11) Refund event publishes PaymentRefundedEvent (sanity)
    // ---------------------------------------------------------------------------------

    @Test
    void chargeRefundedEventPublishesPaymentRefundedEvent() throws Exception {
        // Pre-condition: payment must be SUCCESS for the refund path (the service simply
        // updates by stripePaymentID; no business-rule check today).
        final PaymentEntity payment = paymentAttemptRepository.findByStripePaymentID(stripePaymentId).orElseThrow();
        payment.setStatus(PaymentAttemptStatus.SUCCESS);
        paymentAttemptRepository.save(payment);

        final String payload = chargeRefundedJson(stripePaymentId, orderUuid);
        final StripeEventBuilder.Signed signed = StripeEventBuilder.signedPayloadNow(WEBHOOK_SECRET, payload);

        mockMvc.perform(post(STRIPE_WEBHOOK_ENDPOINT)
                        .header(STRIPE_SIGNATURE_HEADER, signed.header())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signed.payload()))
                .andExpect(status().isOk());

        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(applicationEvents.stream(PaymentRefundedEvent.class).count()).isEqualTo(1L)
        );

        final PaymentEntity reloaded = paymentAttemptRepository.findByStripePaymentID(stripePaymentId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(PaymentAttemptStatus.REFUNDED);
    }

    // ===== JSON helpers — minimal Stripe-compatible event envelopes ===================

    private static String paymentIntentSucceededJson(final String paymentIntentId,
                                                     final UUID orderUuid,
                                                     final String idempotencyKey) {
        return """
                {
                  "id": "evt_%s",
                  "object": "event",
                  "api_version": "2024-04-10",
                  "created": %d,
                  "type": "payment_intent.succeeded",
                  "livemode": false,
                  "pending_webhooks": 0,
                  "request": {"id": null, "idempotency_key": null},
                  "data": {
                    "object": {
                      "id": "%s",
                      "object": "payment_intent",
                      "amount": 10000,
                      "amount_received": 10000,
                      "currency": "eur",
                      "status": "succeeded",
                      "metadata": {
                        "order_uuid": "%s",
                        "idempotency_key": "%s"
                      }
                    }
                  }
                }
                """.formatted(
                UUID.randomUUID().toString().replace("-", ""),
                System.currentTimeMillis() / 1000L,
                paymentIntentId,
                orderUuid,
                idempotencyKey
        );
    }

    private static String paymentIntentFailedJson(final String paymentIntentId,
                                                  final UUID orderUuid,
                                                  final String idempotencyKey) {
        return """
                {
                  "id": "evt_%s",
                  "object": "event",
                  "api_version": "2024-04-10",
                  "created": %d,
                  "type": "payment_intent.payment_failed",
                  "livemode": false,
                  "pending_webhooks": 0,
                  "request": {"id": null, "idempotency_key": null},
                  "data": {
                    "object": {
                      "id": "%s",
                      "object": "payment_intent",
                      "amount": 10000,
                      "currency": "eur",
                      "status": "requires_payment_method",
                      "metadata": {
                        "order_uuid": "%s",
                        "idempotency_key": "%s"
                      }
                    }
                  }
                }
                """.formatted(
                UUID.randomUUID().toString().replace("-", ""),
                System.currentTimeMillis() / 1000L,
                paymentIntentId,
                orderUuid,
                idempotencyKey
        );
    }

    private static String chargeRefundedJson(final String paymentIntentId, final UUID orderUuid) {
        return """
                {
                  "id": "evt_%s",
                  "object": "event",
                  "api_version": "2024-04-10",
                  "created": %d,
                  "type": "charge.refunded",
                  "livemode": false,
                  "pending_webhooks": 0,
                  "request": {"id": null, "idempotency_key": null},
                  "data": {
                    "object": {
                      "id": "ch_%s",
                      "object": "charge",
                      "payment_intent": "%s",
                      "amount": 10000,
                      "amount_refunded": 10000,
                      "currency": "eur",
                      "refunded": true,
                      "metadata": {
                        "order_uuid": "%s"
                      }
                    }
                  }
                }
                """.formatted(
                UUID.randomUUID().toString().replace("-", ""),
                System.currentTimeMillis() / 1000L,
                UUID.randomUUID().toString().replace("-", ""),
                paymentIntentId,
                orderUuid
        );
    }
}
