package com.novatech.cybertech.services.implementation.payment;

import com.novatech.cybertech.entities.OrderEntity;
import com.novatech.cybertech.entities.PaymentEntity;
import com.novatech.cybertech.entities.ProcessedWebhookEventEntity;
import com.novatech.cybertech.entities.enums.PaymentAttemptStatus;
import com.novatech.cybertech.events.OrderPaidEvent;
import com.novatech.cybertech.events.PaymentFailedEvent;
import com.novatech.cybertech.events.PaymentRefundedEvent;
import com.novatech.cybertech.events.PaymentSucceededEvent;
import com.novatech.cybertech.exceptions.PaymentNotFoundException;
import com.novatech.cybertech.fixtures.builders.OrderEntityBuilder;
import com.novatech.cybertech.fixtures.builders.PaymentEntityBuilder;
import com.novatech.cybertech.fixtures.support.stubs.StripeEventBuilder;
import com.novatech.cybertech.repositories.PaymentAttemptRepository;
import com.novatech.cybertech.repositories.ProcessedWebhookEventRepository;
import com.novatech.cybertech.services.implementation.PaymentWebhookServiceImp;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.net.Webhook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PaymentWebhookServiceImp}.
 *
 * <p>The class is wired through with {@link MockitoExtension} (no Spring context). Real Stripe
 * {@link Event} instances are built via {@link Webhook#constructEvent(String, String, String)}
 * over a test-fixture HMAC ({@link StripeEventBuilder}) — this matches the integration test style
 * and exercises the same {@code EventDataObjectDeserializer} path the production code drives.
 *
 * <p>Coverage targets every public-method branch and every guard:
 * <ul>
 *   <li>{@code handleEvent} dedup short-circuit (BUG-170)</li>
 *   <li>{@code handleEvent} livemode mismatch (BUG-522, both directions: livemode=true on a test
 *       deployment AND livemode=false on a prod deployment)</li>
 *   <li>{@code handleEvent} switch — succeeded / failed / refunded / unknown</li>
 *   <li>{@code handlePaymentSucceeded} happy path → publishes {@link PaymentSucceededEvent} +
 *       {@link OrderPaidEvent}, handles missing metadata fallback to PaymentEntity association,
 *       handles invalid UUID metadata (warn-and-fallback), handles null orderEntity (no
 *       OrderPaidEvent published)</li>
 *   <li>{@code handlePaymentFailed} happy path → publishes {@link PaymentFailedEvent}, terminal
 *       SUCCESS guard (BUG-521) drops the failure, missing payment row throws
 *       {@link PaymentNotFoundException}</li>
 *   <li>{@code handleRefund} happy path → publishes {@link PaymentRefundedEvent}</li>
 *   <li>{@code deserializeDataObject} fallback into {@code deserializeUnsafe()} when the event's
 *       api_version differs from the SDK-bundled one (the IT fixtures always use "2024-04-10",
 *       which differs from the SDK's pinned API_VERSION → drives the fallback)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class PaymentWebhookServiceImpTest {

    private static final String WEBHOOK_SECRET = "whsec_test_unit_payment_webhook_service";

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private PaymentAttemptRepository attemptRepository;

    @Mock
    private ProcessedWebhookEventRepository processedWebhookEventRepository;

    @InjectMocks
    private PaymentWebhookServiceImp service;

    @BeforeEach
    void setUp() {
        // The @Value("${stripe.livemode:false}") field is not populated by Spring in a pure unit
        // test — initialise it explicitly here so each test starts from a known state. Default to
        // a "test" deployment (false). Tests that need to flip to "prod" do so via
        // ReflectionTestUtils inline.
        ReflectionTestUtils.setField(service, "expectedLivemode", false);
    }

    // ===== Helpers ==================================================================

    /**
     * Build a real Stripe {@link Event} object from a JSON payload using
     * {@link Webhook#constructEvent(String, String, String)} — the same code path the controller
     * uses in production. The test signs the payload with {@link StripeEventBuilder} so signature
     * verification passes inside {@code constructEvent}.
     */
    private static Event toEvent(final String payload) {
        final StripeEventBuilder.Signed signed = StripeEventBuilder.signedPayloadNow(WEBHOOK_SECRET, payload);
        try {
            return Webhook.constructEvent(signed.payload(), signed.header(), WEBHOOK_SECRET);
        } catch (final SignatureVerificationException e) {
            throw new IllegalStateException("Failed to construct Stripe Event from test fixture", e);
        }
    }

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

    private static String paymentIntentSucceededJsonNoMetadata(final String paymentIntentId) {
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
                      "status": "succeeded"
                    }
                  }
                }
                """.formatted(
                UUID.randomUUID().toString().replace("-", ""),
                System.currentTimeMillis() / 1000L,
                paymentIntentId
        );
    }

    private static String paymentIntentSucceededJsonInvalidUuidMetadata(final String paymentIntentId) {
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
                      "currency": "eur",
                      "status": "succeeded",
                      "metadata": {
                        "order_uuid": "not-a-uuid"
                      }
                    }
                  }
                }
                """.formatted(
                UUID.randomUUID().toString().replace("-", ""),
                System.currentTimeMillis() / 1000L,
                paymentIntentId
        );
    }

    private static String paymentIntentSucceededJsonBlankMetadata(final String paymentIntentId) {
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
                      "currency": "eur",
                      "status": "succeeded",
                      "metadata": {
                        "order_uuid": ""
                      }
                    }
                  }
                }
                """.formatted(
                UUID.randomUUID().toString().replace("-", ""),
                System.currentTimeMillis() / 1000L,
                paymentIntentId
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

    private static String unknownTypeJson() {
        return """
                {
                  "id": "evt_%s",
                  "object": "event",
                  "api_version": "2024-04-10",
                  "created": %d,
                  "type": "customer.subscription.updated",
                  "livemode": false,
                  "pending_webhooks": 0,
                  "request": {"id": null, "idempotency_key": null},
                  "data": {"object": {"id": "sub_xyz", "object": "subscription"}}
                }
                """.formatted(
                UUID.randomUUID().toString().replace("-", ""),
                System.currentTimeMillis() / 1000L
        );
    }

    private PaymentEntity paymentRow(final String stripePaymentId, final OrderEntity order, final PaymentAttemptStatus status) {
        return PaymentEntityBuilder.aValidPaymentBuilder()
                .stripePaymentID(stripePaymentId)
                .orderEntity(order)
                .status(status)
                .build();
    }

    // ===================================================================================
    @Nested
    @DisplayName("handleEvent — dispatch + guards")
    class HandleEventDispatch {

        @Test
        @DisplayName("dedup hit: existing ledger row short-circuits before any side-effect (BUG-170)")
        void dedupHitShortCircuitsBeforeAnySideEffect() {
            final String stripePaymentId = "pi_dedup";
            final UUID orderUuid = UUID.randomUUID();
            final String payload = paymentIntentSucceededJson(stripePaymentId, orderUuid, "idem-1");
            final Event event = toEvent(payload);

            when(processedWebhookEventRepository.existsByStripeEventId(event.getId())).thenReturn(true);

            service.handleEvent(event, payload);

            verify(processedWebhookEventRepository).existsByStripeEventId(event.getId());
            verifyNoMoreInteractions(processedWebhookEventRepository);
            verifyNoInteractions(attemptRepository, eventPublisher);
        }

        @Test
        @DisplayName("livemode=true on a test deployment is silently dropped (BUG-522)")
        void livemodeTrueOnTestDeploymentIsDropped() {
            // expectedLivemode is false (set in @BeforeEach)
            final String paymentIntentId = "pi_livemode_mismatch";
            final String payload = paymentIntentSucceededJson(paymentIntentId, UUID.randomUUID(), "idem-1")
                    .replace("\"livemode\": false", "\"livemode\": true");
            final Event event = toEvent(payload);

            when(processedWebhookEventRepository.existsByStripeEventId(event.getId())).thenReturn(false);

            service.handleEvent(event, payload);

            // Service short-circuits — no payment lookup, no event publish, no ledger write.
            verify(processedWebhookEventRepository).existsByStripeEventId(event.getId());
            verifyNoMoreInteractions(processedWebhookEventRepository);
            verifyNoInteractions(attemptRepository, eventPublisher);
        }

        @Test
        @DisplayName("livemode=false on a prod deployment (expectedLivemode=true) is dropped (BUG-522 reverse)")
        void livemodeFalseOnProdDeploymentIsDropped() {
            ReflectionTestUtils.setField(service, "expectedLivemode", true);
            final String paymentIntentId = "pi_livemode_reverse_mismatch";
            final String payload = paymentIntentSucceededJson(paymentIntentId, UUID.randomUUID(), "idem-1");
            // Payload already has livemode=false → expected=true ⇒ mismatch.
            final Event event = toEvent(payload);

            when(processedWebhookEventRepository.existsByStripeEventId(event.getId())).thenReturn(false);

            service.handleEvent(event, payload);

            verifyNoInteractions(attemptRepository, eventPublisher);
        }

        @Test
        @DisplayName("unknown event type does not record a ledger row (so future handler addition still processes retries)")
        void unknownEventTypeIsNotRecordedInLedger() {
            final String payload = unknownTypeJson();
            final Event event = toEvent(payload);

            when(processedWebhookEventRepository.existsByStripeEventId(event.getId())).thenReturn(false);

            service.handleEvent(event, payload);

            // Existence check ran, but the save() to seal the dedup ledger MUST NOT run.
            verify(processedWebhookEventRepository).existsByStripeEventId(event.getId());
            verify(processedWebhookEventRepository, never()).save(any());
            verifyNoInteractions(attemptRepository, eventPublisher);
        }

        @Test
        @DisplayName("handled event types record a ProcessedWebhookEventEntity row keyed on stripe event id")
        void handledEventTypeRecordsLedgerRow() {
            final String stripePaymentId = "pi_recorded";
            final OrderEntity order = OrderEntityBuilder.aValidOrder();
            final UUID orderUuid = order.getUuid();
            final String payload = paymentIntentSucceededJson(stripePaymentId, orderUuid, "idem-record");
            final Event event = toEvent(payload);

            when(processedWebhookEventRepository.existsByStripeEventId(event.getId())).thenReturn(false);

            final PaymentEntity row = paymentRow(stripePaymentId, order, PaymentAttemptStatus.PROCESSING);
            when(attemptRepository.findByStripePaymentID(stripePaymentId)).thenReturn(Optional.of(row));
            when(attemptRepository.save(any(PaymentEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            service.handleEvent(event, payload);

            final ArgumentCaptor<ProcessedWebhookEventEntity> captor =
                    ArgumentCaptor.forClass(ProcessedWebhookEventEntity.class);
            verify(processedWebhookEventRepository).save(captor.capture());
            assertThat(captor.getValue().getStripeEventId()).isEqualTo(event.getId());
            assertThat(captor.getValue().getProcessedAt()).isNotNull();
        }
    }

    // ===================================================================================
    @Nested
    @DisplayName("handlePaymentSucceeded")
    class HandlePaymentSucceeded {

        @Test
        @DisplayName("happy path: flips payment to SUCCESS, publishes PaymentSucceededEvent + OrderPaidEvent")
        void happyPathFlipsPaymentAndPublishesBothEvents() {
            final String stripePaymentId = "pi_success_happy";
            final OrderEntity order = OrderEntityBuilder.aValidOrder();
            final UUID orderUuid = order.getUuid();

            final String payload = paymentIntentSucceededJson(stripePaymentId, orderUuid, "idem-h");
            final Event event = toEvent(payload);

            when(processedWebhookEventRepository.existsByStripeEventId(event.getId())).thenReturn(false);
            final PaymentEntity row = paymentRow(stripePaymentId, order, PaymentAttemptStatus.PROCESSING);
            when(attemptRepository.findByStripePaymentID(stripePaymentId)).thenReturn(Optional.of(row));
            when(attemptRepository.save(any(PaymentEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            service.handleEvent(event, payload);

            assertThat(row.getStatus()).isEqualTo(PaymentAttemptStatus.SUCCESS);
            assertThat(row.getProviderEventId()).isEqualTo(event.getId());

            verify(eventPublisher).publishEvent(any(PaymentSucceededEvent.class));

            final ArgumentCaptor<OrderPaidEvent> orderPaidCaptor = ArgumentCaptor.forClass(OrderPaidEvent.class);
            verify(eventPublisher).publishEvent(orderPaidCaptor.capture());
            assertThat(orderPaidCaptor.getValue().getOrderUUID()).isEqualTo(orderUuid);
        }

        @Test
        @DisplayName("missing metadata.order_uuid falls back to payment.orderEntity.uuid")
        void missingMetadataFallsBackToPaymentOrderEntityUuid() {
            final String stripePaymentId = "pi_no_metadata";
            final OrderEntity order = OrderEntityBuilder.aValidOrder();
            final UUID expectedUuid = order.getUuid();

            final String payload = paymentIntentSucceededJsonNoMetadata(stripePaymentId);
            final Event event = toEvent(payload);

            when(processedWebhookEventRepository.existsByStripeEventId(event.getId())).thenReturn(false);
            final PaymentEntity row = paymentRow(stripePaymentId, order, PaymentAttemptStatus.PROCESSING);
            when(attemptRepository.findByStripePaymentID(stripePaymentId)).thenReturn(Optional.of(row));
            when(attemptRepository.save(any(PaymentEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            service.handleEvent(event, payload);

            verify(eventPublisher).publishEvent(any(PaymentSucceededEvent.class));
            final ArgumentCaptor<OrderPaidEvent> captor = ArgumentCaptor.forClass(OrderPaidEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());
            assertThat(captor.getValue().getOrderUUID()).isEqualTo(expectedUuid);
        }

        @Test
        @DisplayName("blank metadata.order_uuid falls back to payment.orderEntity.uuid")
        void blankMetadataFallsBackToOrderEntityUuid() {
            final String stripePaymentId = "pi_blank_metadata";
            final OrderEntity order = OrderEntityBuilder.aValidOrder();
            final UUID expectedUuid = order.getUuid();

            final String payload = paymentIntentSucceededJsonBlankMetadata(stripePaymentId);
            final Event event = toEvent(payload);

            when(processedWebhookEventRepository.existsByStripeEventId(event.getId())).thenReturn(false);
            final PaymentEntity row = paymentRow(stripePaymentId, order, PaymentAttemptStatus.PROCESSING);
            when(attemptRepository.findByStripePaymentID(stripePaymentId)).thenReturn(Optional.of(row));
            when(attemptRepository.save(any(PaymentEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            service.handleEvent(event, payload);

            final ArgumentCaptor<OrderPaidEvent> captor = ArgumentCaptor.forClass(OrderPaidEvent.class);
            verify(eventPublisher).publishEvent(any(PaymentSucceededEvent.class));
            verify(eventPublisher).publishEvent(captor.capture());
            assertThat(captor.getValue().getOrderUUID()).isEqualTo(expectedUuid);
        }

        @Test
        @DisplayName("invalid UUID metadata is logged warn-level and falls back to payment.orderEntity.uuid")
        void invalidUuidMetadataFallsBackToOrderEntityUuid() {
            final String stripePaymentId = "pi_invalid_uuid_metadata";
            final OrderEntity order = OrderEntityBuilder.aValidOrder();
            final UUID expectedUuid = order.getUuid();

            final String payload = paymentIntentSucceededJsonInvalidUuidMetadata(stripePaymentId);
            final Event event = toEvent(payload);

            when(processedWebhookEventRepository.existsByStripeEventId(event.getId())).thenReturn(false);
            final PaymentEntity row = paymentRow(stripePaymentId, order, PaymentAttemptStatus.PROCESSING);
            when(attemptRepository.findByStripePaymentID(stripePaymentId)).thenReturn(Optional.of(row));
            when(attemptRepository.save(any(PaymentEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            service.handleEvent(event, payload);

            final ArgumentCaptor<OrderPaidEvent> captor = ArgumentCaptor.forClass(OrderPaidEvent.class);
            verify(eventPublisher).publishEvent(any(PaymentSucceededEvent.class));
            verify(eventPublisher).publishEvent(captor.capture());
            assertThat(captor.getValue().getOrderUUID()).isEqualTo(expectedUuid);
        }

        @Test
        @DisplayName("when no metadata AND payment.orderEntity is null — only PaymentSucceededEvent is published, no OrderPaidEvent")
        void noMetadataAndNullOrderEntitySkipsOrderPaidEvent() {
            final String stripePaymentId = "pi_no_order_anywhere";

            final String payload = paymentIntentSucceededJsonNoMetadata(stripePaymentId);
            final Event event = toEvent(payload);

            when(processedWebhookEventRepository.existsByStripeEventId(event.getId())).thenReturn(false);

            // Build a payment row with no orderEntity (the builder normally seeds one, override to null)
            final PaymentEntity row = PaymentEntityBuilder.aValidPaymentBuilder()
                    .stripePaymentID(stripePaymentId)
                    .status(PaymentAttemptStatus.PROCESSING)
                    .orderEntity(null)
                    .build();
            when(attemptRepository.findByStripePaymentID(stripePaymentId)).thenReturn(Optional.of(row));
            when(attemptRepository.save(any(PaymentEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            service.handleEvent(event, payload);

            verify(eventPublisher).publishEvent(any(PaymentSucceededEvent.class));
            verify(eventPublisher, never()).publishEvent(any(OrderPaidEvent.class));
        }

        @Test
        @DisplayName("missing payment row throws PaymentNotFoundException — no events, no ledger insert")
        void missingPaymentRowThrows() {
            final String stripePaymentId = "pi_orphan";
            final String payload = paymentIntentSucceededJson(stripePaymentId, UUID.randomUUID(), "idem-orphan");
            final Event event = toEvent(payload);

            when(processedWebhookEventRepository.existsByStripeEventId(event.getId())).thenReturn(false);
            when(attemptRepository.findByStripePaymentID(stripePaymentId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.handleEvent(event, payload))
                    .isInstanceOf(PaymentNotFoundException.class)
                    .hasMessageContaining(stripePaymentId);

            verifyNoInteractions(eventPublisher);
            verify(processedWebhookEventRepository, never()).save(any());
        }
    }

    // ===================================================================================
    @Nested
    @DisplayName("handlePaymentFailed")
    class HandlePaymentFailed {

        @Test
        @DisplayName("happy path: flips payment to FAILED and publishes PaymentFailedEvent (BUG-520)")
        void happyPathFlipsToFailedAndPublishes() {
            final String stripePaymentId = "pi_failed_happy";
            final OrderEntity order = OrderEntityBuilder.aValidOrder();
            final String payload = paymentIntentFailedJson(stripePaymentId, order.getUuid(), "idem-f");
            final Event event = toEvent(payload);

            when(processedWebhookEventRepository.existsByStripeEventId(event.getId())).thenReturn(false);
            final PaymentEntity row = paymentRow(stripePaymentId, order, PaymentAttemptStatus.PROCESSING);
            when(attemptRepository.findByStripePaymentID(stripePaymentId)).thenReturn(Optional.of(row));
            when(attemptRepository.save(any(PaymentEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            service.handleEvent(event, payload);

            assertThat(row.getStatus()).isEqualTo(PaymentAttemptStatus.FAILED);
            assertThat(row.getProviderEventId()).isEqualTo(event.getId());
            verify(eventPublisher).publishEvent(any(PaymentFailedEvent.class));
            // No OrderPaidEvent is published on failure
            verify(eventPublisher, never()).publishEvent(any(OrderPaidEvent.class));
        }

        @Test
        @DisplayName("terminal SUCCESS guard drops out-of-order failure (BUG-521): no save, no event")
        void terminalSuccessGuardDropsFailure() {
            final String stripePaymentId = "pi_already_success";
            final OrderEntity order = OrderEntityBuilder.aValidOrder();
            final String payload = paymentIntentFailedJson(stripePaymentId, order.getUuid(), "idem-tg");
            final Event event = toEvent(payload);

            when(processedWebhookEventRepository.existsByStripeEventId(event.getId())).thenReturn(false);
            final PaymentEntity row = paymentRow(stripePaymentId, order, PaymentAttemptStatus.SUCCESS);
            when(attemptRepository.findByStripePaymentID(stripePaymentId)).thenReturn(Optional.of(row));

            service.handleEvent(event, payload);

            // Status stays SUCCESS, no save, no event — but ledger row still recorded since the
            // event WAS handled (just dropped by the guard).
            assertThat(row.getStatus()).isEqualTo(PaymentAttemptStatus.SUCCESS);
            verify(attemptRepository, never()).save(any());
            verify(eventPublisher, never()).publishEvent(any());
            verify(processedWebhookEventRepository, times(1)).save(any(ProcessedWebhookEventEntity.class));
        }

        @Test
        @DisplayName("missing payment row throws PaymentNotFoundException")
        void missingPaymentRowThrowsOnFailure() {
            final String stripePaymentId = "pi_orphan_failed";
            final String payload = paymentIntentFailedJson(stripePaymentId, UUID.randomUUID(), "idem-x");
            final Event event = toEvent(payload);

            when(processedWebhookEventRepository.existsByStripeEventId(event.getId())).thenReturn(false);
            when(attemptRepository.findByStripePaymentID(stripePaymentId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.handleEvent(event, payload))
                    .isInstanceOf(PaymentNotFoundException.class)
                    .hasMessageContaining(stripePaymentId);

            verifyNoInteractions(eventPublisher);
            verify(processedWebhookEventRepository, never()).save(any());
        }
    }

    // ===================================================================================
    @Nested
    @DisplayName("handleRefund")
    class HandleRefund {

        @Test
        @DisplayName("happy path: flips payment to REFUNDED and publishes PaymentRefundedEvent")
        void happyPathFlipsToRefundedAndPublishes() {
            final String stripePaymentId = "pi_refund_happy";
            final OrderEntity order = OrderEntityBuilder.aValidOrder();
            final String payload = chargeRefundedJson(stripePaymentId, order.getUuid());
            final Event event = toEvent(payload);

            when(processedWebhookEventRepository.existsByStripeEventId(event.getId())).thenReturn(false);
            final PaymentEntity row = paymentRow(stripePaymentId, order, PaymentAttemptStatus.SUCCESS);
            when(attemptRepository.findByStripePaymentID(stripePaymentId)).thenReturn(Optional.of(row));
            when(attemptRepository.save(any(PaymentEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            service.handleEvent(event, payload);

            assertThat(row.getStatus()).isEqualTo(PaymentAttemptStatus.REFUNDED);
            assertThat(row.getProviderEventId()).isEqualTo(event.getId());
            verify(eventPublisher).publishEvent(any(PaymentRefundedEvent.class));
            // The refund path does NOT publish OrderPaidEvent or PaymentSucceededEvent
            verify(eventPublisher, never()).publishEvent(any(PaymentSucceededEvent.class));
            verify(eventPublisher, never()).publishEvent(any(OrderPaidEvent.class));
        }

        @Test
        @DisplayName("missing payment row on refund throws PaymentNotFoundException")
        void missingPaymentRowOnRefundThrows() {
            final String stripePaymentId = "pi_orphan_refund";
            final String payload = chargeRefundedJson(stripePaymentId, UUID.randomUUID());
            final Event event = toEvent(payload);

            when(processedWebhookEventRepository.existsByStripeEventId(event.getId())).thenReturn(false);
            when(attemptRepository.findByStripePaymentID(stripePaymentId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.handleEvent(event, payload))
                    .isInstanceOf(PaymentNotFoundException.class)
                    .hasMessageContaining(stripePaymentId);

            verifyNoInteractions(eventPublisher);
            verify(processedWebhookEventRepository, never()).save(any());
        }
    }

    // ===================================================================================
    @Nested
    @DisplayName("deserializeDataObject — API_VERSION skew fallback")
    class DeserializeDataObjectFallback {

        /**
         * Sanity check on the API_VERSION skew assumption: the IT fixtures (and the JSON helpers in
         * this class) all use api_version "2024-04-10" while the bundled SDK pins
         * "2026-02-25.clover". The deserializer's {@link EventDataObjectDeserializer#getObject()}
         * therefore returns {@code Optional.empty()} on every test event, which means
         * {@code deserializeUnsafe()} is the path that actually returns the {@code PaymentIntent} /
         * {@code Charge} we then cast in production.
         *
         * <p>This isn't a test of {@code PaymentWebhookServiceImp} per se — it's a guard against the
         * SDK upgrade that closes the skew (and would otherwise silently change which branch is
         * taken in every test above).
         */
        @Test
        @DisplayName("test fixtures' api_version mismatches the bundled SDK → getObject() is empty, drives deserializeUnsafe path")
        void apiVersionSkewSanityCheck() {
            final String payload = paymentIntentSucceededJson("pi_skew_check", UUID.randomUUID(), "idem-skew");
            final Event event = toEvent(payload);

            assertThat(event.getDataObjectDeserializer().getObject())
                    .as("fixture api_version=2024-04-10 differs from SDK-bundled API_VERSION; getObject() must be empty")
                    .isEmpty();
        }

        /**
         * The defensive {@code catch (EventDataObjectDeserializationException)} branch in
         * {@code deserializeDataObject} (lines 257-263 of the production source) re-wraps a Stripe
         * SDK deserialization failure as an {@link IllegalStateException}. To trigger it we'd need
         * a {@code data.object} where:
         * <ol>
         *   <li>The top-level event envelope is still valid JSON (so {@code Webhook.constructEvent}
         *       accepts it)</li>
         *   <li>Jackson's {@code mapper.readValue} for the rich {@code StripeWebhookEventDto} also
         *       accepts it (so we make it past line 118)</li>
         *   <li>Stripe's typed {@code PaymentIntent} / {@code Charge} deserialization fails when
         *       it tries to coerce a field</li>
         * </ol>
         *
         * <p>In practice (1) and (2) make (3) virtually impossible — any field shape that breaks
         * the Stripe model also breaks our looser-typed Jackson DTO, and the failure surfaces
         * earlier as {@code tools.jackson.databind.exc.InvalidFormatException}. The Stripe catch
         * is therefore a defensive contract guarantee rather than a routinely-hit branch. Keeping
         * a JaCoCo line-miss here (5 lines = ~5.8% of the class) is the honest outcome — adding a
         * test that abuses Mockito to mock-mock the Stripe {@code Event} class would assert
         * implementation rather than behaviour.
         */
    }
}
