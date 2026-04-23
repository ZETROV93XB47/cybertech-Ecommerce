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
import org.junit.jupiter.api.Disabled;
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
 * <p><b>Critical F1 verification (verified by direct file-read of {@code src/main}):</b>
 * <ul>
 *   <li><b>BUG-170</b> — F1.4 claimed it added {@code ProcessedWebhookEventEntity} for dedup.
 *       File search confirms NO such entity exists in {@code src/main}; replay still fires
 *       {@link PaymentSucceededEvent} on every call. PINNED via current-behaviour test.</li>
 *   <li><b>BUG-171</b> — F1.4 claimed {@link OrderPaidEvent} is now published from the webhook
 *       path. Direct read of {@code PaymentWebhookServiceImp} confirms it is NOT published
 *       anywhere; the listener flips the order status via {@link PaymentSucceededEvent} only.
 *       Desired contract pinned with {@code @Disabled("BUG-171")}.</li>
 *   <li><b>BUG-2501</b> — F1.4 claimed the controller now ACKs 200 on non-retriable. Direct
 *       read shows the controller still has no try/catch around {@code service.handleEvent};
 *       orphan PaymentIntent surfaces as a non-2xx (404 via F1.1's PaymentNotFoundException
 *       handler). Pinned current behaviour + desired-disabled.</li>
 *   <li><b>BUG-2502</b> — Signed-but-malformed JSON still surfaces as 5xx (the SDK throws a
 *       {@code JsonSyntaxException} BEFORE signature verification, the controller does not
 *       catch it). Pinned + desired-disabled.</li>
 *   <li><b>BUG-172</b> — {@code PaymentDtoFixtures} correctly uses {@code metadata.order_uuid}
 *       (matches the listener's expectation) — verified green by source read.</li>
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
     * Sending the SAME signed event twice today causes {@link PaymentSucceededEvent} to be
     * published TWICE — the service has no dedup table. F1.4 claimed to add
     * {@code ProcessedWebhookEventEntity} but a {@code Glob} of {@code src/main} returns
     * zero matches. Pin the current (buggy) behaviour so the test flips RED the moment dedup
     * is wired and the count drops to 1.
     *
     * <p>The desired contract is captured in the {@code @Disabled("BUG-170")} sibling below.
     */
    @Test
    void replayOfSameEventRepublishesSucceededEvent_notDeduped_BUG_170() throws Exception {
        final String payload = paymentIntentSucceededJson(stripePaymentId, orderUuid, idempotencyKey);
        final StripeEventBuilder.Signed signed = StripeEventBuilder.signedPayloadNow(WEBHOOK_SECRET, payload);

        // Send the same signed event twice
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

        // Today: 2 publishes (no dedup). When BUG-170 is fixed this becomes 1 → test flips red,
        // forcing a follow-up to enable the desired pin below.
        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(applicationEvents.stream(PaymentSucceededEvent.class).count())
                        .as("BUG-170: replay re-publishes; F1.4's ProcessedWebhookEventEntity NOT in tree")
                        .isEqualTo(2L)
        );
    }

    @Test
    @Disabled("BUG-170 — webhook should dedup by event.id (Stripe Event ID) so a replayed event " +
            "publishes PaymentSucceededEvent only once. F1.4 claimed ProcessedWebhookEventEntity " +
            "was added for this purpose, but a file search confirms it does not exist in src/main. " +
            "Flip green when the entity + dedup check land in PaymentWebhookServiceImp.")
    void replayOfSameEventShouldPublishOnlyOnce_BUG_170_desired() throws Exception {
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
     * A valid HMAC over a non-JSON payload causes the Stripe SDK's {@code constructEvent} to throw
     * a {@code JsonSyntaxException} (RuntimeException) BEFORE signature verification runs. The
     * controller has no catch for that and the global advice's {@code RuntimeException} catch-all
     * wraps it as 500. Pin the current behaviour and capture the desired 400 via {@code @Disabled}.
     */
    @Test
    void malformedJsonWithValidSignatureSurfacesAs5xx_BUG_2502_currentBehaviour() throws Exception {
        final String malformed = "{\"id\":\"evt_test\",\"this is not valid JSON";
        final StripeEventBuilder.Signed signed = StripeEventBuilder.signedPayloadNow(WEBHOOK_SECRET, malformed);

        mockMvc.perform(post(STRIPE_WEBHOOK_ENDPOINT)
                        .header(STRIPE_SIGNATURE_HEADER, signed.header())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signed.payload()))
                .andExpect(result -> {
                    final int s = result.getResponse().getStatus();
                    assertThat(s)
                            .as("BUG-2502: signed-but-malformed JSON still leaks as 5xx today")
                            .isBetween(500, 599);
                });
    }

    @Test
    @Disabled("BUG-2502 — signed-but-malformed JSON should return 400 BAD_REQUEST. Today the SDK " +
            "throws JsonSyntaxException from constructEvent BEFORE signature verification, the " +
            "controller does not catch it, and the global advice wraps it as 500. Flip green when " +
            "the controller catches RuntimeException from constructEvent and returns 400.")
    void malformedJsonWithValidSignatureShouldReturn400_BUG_2502_desired() throws Exception {
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
     * Direct read of {@code PaymentWebhookServiceImp#handlePaymentFailed} confirms it updates the
     * payment status to FAILED but DOES NOT call {@code eventPublisher.publishEvent(new
     * PaymentFailedEvent(...))}. The {@code OrderPaymentConfirmationEventListener#handlePaymentFailed}
     * is therefore dead code on the webhook path. Pin the current (buggy) behaviour as a passing
     * test that locks the row update + zero event publish.
     */
    @Test
    void paymentIntentFailedUpdatesStatusButDoesNotPublishFailedEvent_BUG_520() throws Exception {
        final String payload = paymentIntentFailedJson(stripePaymentId, orderUuid, idempotencyKey);
        final StripeEventBuilder.Signed signed = StripeEventBuilder.signedPayloadNow(WEBHOOK_SECRET, payload);

        mockMvc.perform(post(STRIPE_WEBHOOK_ENDPOINT)
                        .header(STRIPE_SIGNATURE_HEADER, signed.header())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signed.payload()))
                .andExpect(status().isOk());

        // DB side is updated
        final PaymentEntity reloaded = paymentAttemptRepository.findByStripePaymentID(stripePaymentId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(PaymentAttemptStatus.FAILED);

        // BUG-520: the service does not publish PaymentFailedEvent — no listener fires.
        assertThat(applicationEvents.stream(PaymentFailedEvent.class).count())
                .as("BUG-520: PaymentWebhookServiceImp#handlePaymentFailed never publishes PaymentFailedEvent")
                .isZero();
    }

    @Test
    @Disabled("BUG-520 — PaymentWebhookServiceImp#handlePaymentFailed should publish " +
            "PaymentFailedEvent so OrderPaymentConfirmationEventListener#handlePaymentFailed can " +
            "release the stock and flip the order to PAYMENT_FAILED. Today the listener is dead " +
            "code on the webhook path. Flip green by adding eventPublisher.publishEvent(new " +
            "PaymentFailedEvent(stripeWebhookEventDto)) at the end of handlePaymentFailed.")
    void paymentIntentFailedShouldPublishFailedEvent_BUG_520_desired() throws Exception {
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
     * If Stripe delivers SUCCESS then a delayed FAILED for the same {@code payment_intent},
     * the service today blindly overwrites status from SUCCESS to FAILED — the order would have
     * been moved to PAID by the listener already, but the payment row regresses. Pin the
     * current (buggy) behaviour: the second event WINS at the row level. A future fix should
     * either (a) treat SUCCESS as terminal or (b) track an ordering field on the payment.
     */
    @Test
    void outOfOrderSuccessThenFailedRegressesPaymentStatus_BUG_521() throws Exception {
        final String successPayload = paymentIntentSucceededJson(stripePaymentId, orderUuid, idempotencyKey);
        final StripeEventBuilder.Signed signed1 = StripeEventBuilder.signedPayloadNow(WEBHOOK_SECRET, successPayload);
        mockMvc.perform(post(STRIPE_WEBHOOK_ENDPOINT)
                        .header(STRIPE_SIGNATURE_HEADER, signed1.header())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signed1.payload()))
                .andExpect(status().isOk());

        // After SUCCESS — row is SUCCESS
        assertThat(paymentAttemptRepository.findByStripePaymentID(stripePaymentId).orElseThrow().getStatus())
                .isEqualTo(PaymentAttemptStatus.SUCCESS);

        // Now an out-of-order FAILED for the SAME payment_intent
        final String failedPayload = paymentIntentFailedJson(stripePaymentId, orderUuid, idempotencyKey);
        final StripeEventBuilder.Signed signed2 = StripeEventBuilder.signedPayloadNow(WEBHOOK_SECRET, failedPayload);
        mockMvc.perform(post(STRIPE_WEBHOOK_ENDPOINT)
                        .header(STRIPE_SIGNATURE_HEADER, signed2.header())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signed2.payload()))
                .andExpect(status().isOk());

        // BUG-521: the row regresses to FAILED. The desired-contract test below pins the fix.
        assertThat(paymentAttemptRepository.findByStripePaymentID(stripePaymentId).orElseThrow().getStatus())
                .as("BUG-521: out-of-order FAILED after SUCCESS regresses the payment row")
                .isEqualTo(PaymentAttemptStatus.FAILED);
    }

    @Test
    @Disabled("BUG-521 — once a payment row is in SUCCESS, a subsequent payment_intent.payment_failed " +
            "(out-of-order Stripe delivery) should NOT regress the row. Today the second event wins " +
            "blindly. Flip green by short-circuiting handlePaymentFailed when the existing status is " +
            "already SUCCESS / REFUNDED.")
    void outOfOrderSuccessThenFailedShouldKeepSuccessTerminal_BUG_521_desired() throws Exception {
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
     * A {@code payment_intent.succeeded} with a {@code pi_*} that has NO matching DB row causes
     * {@code PaymentNotFoundException} to bubble out of the service. F1.1 added a 404 handler for
     * that exception; F1.4 claimed BUG-2501 was closed by 200-ACK in the controller, but a direct
     * read shows the controller still does not catch service exceptions. So today: 404 leaks (was
     * 500 pre-F1.1, is 404 post-F1.1, neither 2xx — Stripe will retry).
     */
    @Test
    void orphanPaymentIntentSurfacesAsClientOrServerError_BUG_2501_currentBehaviour() throws Exception {
        final String orphanPi = "pi_orphan_" + UUID.randomUUID().toString().replace("-", "");
        final String payload = paymentIntentSucceededJson(orphanPi, orderUuid, "idem-" + UUID.randomUUID());
        final StripeEventBuilder.Signed signed = StripeEventBuilder.signedPayloadNow(WEBHOOK_SECRET, payload);

        mockMvc.perform(post(STRIPE_WEBHOOK_ENDPOINT)
                        .header(STRIPE_SIGNATURE_HEADER, signed.header())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signed.payload()))
                .andExpect(result -> {
                    final int s = result.getResponse().getStatus();
                    // Tolerant range: 404 today (post-F1.1), 5xx pre-F1.1. Either is non-2xx, which
                    // is the BUG-2501 retry-storm vector.
                    assertThat(s)
                            .as("BUG-2501: orphan PaymentIntent still surfaces as non-2xx (controller has no try/catch)")
                            .isBetween(400, 599);
                });
    }

    @Test
    @Disabled("BUG-2501 — controller should ACK 200 on non-retriable service failures (orphan " +
            "PaymentIntent / unknown order id). Today any service exception leaks as a non-2xx, " +
            "causing Stripe to retry. Flip green when the controller wraps service.handleEvent in " +
            "a try/catch that 200-ACKs unrecoverable cases.")
    void orphanPaymentIntentShouldAck200_BUG_2501_desired() throws Exception {
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
     * The service today does NOT inspect {@code livemode}: a {@code livemode=true} event in a
     * test deployment is processed normally. That's a footgun — a misconfigured prod-secret
     * leaking into a test environment would mutate real-money payment rows. Pin the current
     * permissive behaviour; desired is a defensive 4xx / no-op guard.
     */
    @Test
    void livemodeTrueIsAcceptedInTestEnvironment_BUG_522() throws Exception {
        // livemode=true; everything else identical to the SUCCESS happy path
        final String payload = paymentIntentSucceededJson(stripePaymentId, orderUuid, idempotencyKey)
                .replace("\"livemode\": false", "\"livemode\": true");
        final StripeEventBuilder.Signed signed = StripeEventBuilder.signedPayloadNow(WEBHOOK_SECRET, payload);

        mockMvc.perform(post(STRIPE_WEBHOOK_ENDPOINT)
                        .header(STRIPE_SIGNATURE_HEADER, signed.header())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signed.payload()))
                .andExpect(status().isOk());

        // Today the service does not gate on livemode → row flips to SUCCESS.
        final PaymentEntity reloaded = paymentAttemptRepository.findByStripePaymentID(stripePaymentId).orElseThrow();
        assertThat(reloaded.getStatus())
                .as("BUG-522: service does not validate livemode against the deployment env")
                .isEqualTo(PaymentAttemptStatus.SUCCESS);
    }

    @Test
    @Disabled("BUG-522 — webhook should reject livemode=true events when the application is not " +
            "running in production (and vice-versa). Today the service ignores livemode entirely, " +
            "so a misconfigured prod-secret leaking into a test env would mutate real payments. " +
            "Flip green when livemode is matched against an `app.env` / `stripe.livemode` property.")
    void livemodeTrueShouldBeRejectedInTestEnvironment_BUG_522_desired() throws Exception {
        final String payload = paymentIntentSucceededJson(stripePaymentId, orderUuid, idempotencyKey)
                .replace("\"livemode\": false", "\"livemode\": true");
        final StripeEventBuilder.Signed signed = StripeEventBuilder.signedPayloadNow(WEBHOOK_SECRET, payload);

        mockMvc.perform(post(STRIPE_WEBHOOK_ENDPOINT)
                        .header(STRIPE_SIGNATURE_HEADER, signed.header())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signed.payload()))
                .andExpect(result -> {
                    final int s = result.getResponse().getStatus();
                    assertThat(s).isBetween(400, 499);
                });
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
     * Direct source-read of {@code PaymentWebhookServiceImp} confirms it never instantiates or
     * publishes {@link OrderPaidEvent}. Grep across {@code src/main} confirms no other code path
     * publishes it either. So a happy-path SUCCESS webhook will publish ZERO OrderPaidEvents.
     * Pin the current (buggy) behaviour and capture the desired contract via {@code @Disabled}.
     */
    @Test
    void orderPaidEventIsNeverPublishedOnWebhookSuccess_BUG_171() throws Exception {
        final String payload = paymentIntentSucceededJson(stripePaymentId, orderUuid, idempotencyKey);
        final StripeEventBuilder.Signed signed = StripeEventBuilder.signedPayloadNow(WEBHOOK_SECRET, payload);

        mockMvc.perform(post(STRIPE_WEBHOOK_ENDPOINT)
                        .header(STRIPE_SIGNATURE_HEADER, signed.header())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signed.payload()))
                .andExpect(status().isOk());

        // BUG-171: zero OrderPaidEvent today. Flip red the moment the publish lands.
        Awaitility.await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                assertThat(applicationEvents.stream(OrderPaidEvent.class).count())
                        .as("BUG-171: OrderPaidEvent is declared but never published from the webhook path")
                        .isZero()
        );
    }

    @Test
    @Disabled("BUG-171 — OrderPaidEvent should be published once the order has been flipped to PAID " +
            "by handlePaymentSuccess. F1.4 self-reported this as fixed but a source read confirms it " +
            "is still missing. Flip green by publishing the event from the listener (or service) " +
            "after orderRepository.save(order) lands.")
    void orderPaidEventShouldBePublishedAfterOrderFlipsToPaid_BUG_171_desired() throws Exception {
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
