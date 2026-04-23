package com.novatech.cybertech.services.implementation.payment.core;

import com.novatech.cybertech.dto.data.PaymentAttemptResult;
import com.novatech.cybertech.entities.OrderEntity;
import com.novatech.cybertech.entities.PaymentEntity;
import com.novatech.cybertech.entities.enums.PaymentAttemptStatus;
import com.novatech.cybertech.entities.enums.PaymentType;
import com.novatech.cybertech.entities.enums.TransactionType;
import com.novatech.cybertech.entities.valueObjects.CurrencyCode;
import com.novatech.cybertech.entities.valueObjects.Money;
import com.novatech.cybertech.events.PaymentFailedEvent;
import com.novatech.cybertech.events.PaymentSucceededEvent;
import com.novatech.cybertech.exceptions.PaymentAlreadyCompletedForThisOrderException;
import com.novatech.cybertech.exceptions.PaymentNotFoundException;
import com.novatech.cybertech.exceptions.PaymentProcessingException;
import com.novatech.cybertech.factory.PaymentStrategyFactory;
import com.novatech.cybertech.fixtures.builders.OrderEntityBuilder;
import com.novatech.cybertech.fixtures.builders.PaymentEntityBuilder;
import com.novatech.cybertech.fixtures.dto.PaymentDtoFixtures;
import com.novatech.cybertech.repositories.PaymentAttemptRepository;
import com.novatech.cybertech.services.core.IdempotencyKeyServiceGenerator;
import com.novatech.cybertech.services.core.PaymentAttemptProcessor;
import com.novatech.cybertech.services.implementation.PaymentServiceImp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PaymentServiceImp}.
 *
 * Skeptical audit context (per progress.md SA3.2a + Wave F2):
 * <ul>
 *   <li>BUG-070 (status: open in F2 — partial fix in current source)</li>
 *   <li>BUG-071 (status: open) — null idempotency key not guarded</li>
 *   <li>BUG-072 (status: open) — null order NPEs</li>
 *   <li>BUG-075/076/077 — fixed in F2 (verified in StripePaymentAttemptProcessorTest)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceImpTest {

    @Mock
    private PaymentStrategyFactory paymentStrategyFactory;

    @Mock
    private PaymentAttemptRepository paymentAttemptRepository;

    @Mock
    private IdempotencyKeyServiceGenerator idempotencyKeyService;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Mock
    private PaymentAttemptProcessor processor;

    @InjectMocks
    private PaymentServiceImp service;

    // ---------- helpers ----------

    private OrderEntity newOrder() {
        return OrderEntityBuilder.aValidOrderBuilder().uuid(UUID.randomUUID()).build();
    }

    private Money tenEur() {
        return new Money(new BigDecimal("10.00"), CurrencyCode.EUR);
    }

    /**
     * Repository.save returns its argument identity-mapped (mimicking JPA's behavior of returning
     * the persisted entity).
     */
    private void wireSaveReturnsArg() {
        when(paymentAttemptRepository.save(any(PaymentEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Nested
    @DisplayName("processPayment")
    class ProcessPayment {

        @Test
        @DisplayName("happy path: delegates to processor, persists PaymentEntity, returns it")
        void happyPath_delegatesToProcessor_persistsAndReturns() {
            final OrderEntity order = newOrder();
            final String key = "idem-" + UUID.randomUUID();
            final Money amount = tenEur();

            when(paymentAttemptRepository.findByIdempotencyKey(key)).thenReturn(Optional.empty());
            when(paymentStrategyFactory.getServiceFromPaymentType(PaymentType.VISA)).thenReturn(processor);
            when(processor.processPayment(order.getUuid(), amount, key))
                    .thenReturn(new PaymentAttemptResult(PaymentAttemptStatus.SUCCESS, "pi_123"));
            wireSaveReturnsArg();

            final PaymentEntity result = service.processPayment(order, PaymentType.VISA, amount, key);

            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(PaymentAttemptStatus.SUCCESS);
            assertThat(result.getStripePaymentID()).isEqualTo("pi_123");
            assertThat(result.getIdempotencyKey()).isEqualTo(key);
            assertThat(result.getOrderEntity()).isSameAs(order);
            assertThat(result.getAmount()).isEqualTo(amount);
            assertThat(result.getPaymentType()).isEqualTo(PaymentType.VISA);
            assertThat(result.getTransactionType()).isEqualTo(TransactionType.PAYMENT);

            verify(processor, times(1)).processPayment(order.getUuid(), amount, key);
            verify(paymentAttemptRepository, times(2)).save(any(PaymentEntity.class));
        }

        @Test
        @DisplayName("save→processor→save order is preserved (InOrder)")
        void persistsBeforeAndAfterProcessor() {
            final OrderEntity order = newOrder();
            final String key = "idem-" + UUID.randomUUID();
            when(paymentAttemptRepository.findByIdempotencyKey(key)).thenReturn(Optional.empty());
            when(paymentStrategyFactory.getServiceFromPaymentType(PaymentType.VISA)).thenReturn(processor);
            when(processor.processPayment(any(), any(), any()))
                    .thenReturn(new PaymentAttemptResult(PaymentAttemptStatus.SUCCESS, "pi_inorder"));
            wireSaveReturnsArg();

            service.processPayment(order, PaymentType.VISA, tenEur(), key);

            final InOrder ord = inOrder(paymentAttemptRepository, processor);
            ord.verify(paymentAttemptRepository).save(any(PaymentEntity.class));
            ord.verify(processor).processPayment(any(), any(), any());
            ord.verify(paymentAttemptRepository).save(any(PaymentEntity.class));
        }

        @Test
        @DisplayName("duplicate detection: existing SUCCESS attempt → throws PaymentAlreadyCompletedForThisOrderException")
        void duplicate_alreadySucceeded_throws() {
            final OrderEntity order = newOrder();
            final String key = "idem-dup";
            final PaymentEntity existing = PaymentEntityBuilder.aValidPaymentBuilder()
                    .status(PaymentAttemptStatus.SUCCESS)
                    .idempotencyKey(key)
                    .build();
            when(paymentAttemptRepository.findByIdempotencyKey(key)).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> service.processPayment(order, PaymentType.VISA, tenEur(), key))
                    .isInstanceOf(PaymentAlreadyCompletedForThisOrderException.class)
                    .hasMessageContaining("already completed");

            verify(paymentAttemptRepository, never()).save(any());
            verifyNoInteractions(paymentStrategyFactory, processor, applicationEventPublisher);
        }

        @Test
        @DisplayName("retry: existing FAILED attempt for same key → re-charges (no exception)")
        void retry_existingFailedAttempt_proceedsWithNewAttempt() {
            final OrderEntity order = newOrder();
            final String key = "idem-retry";
            final PaymentEntity existing = PaymentEntityBuilder.aValidPaymentBuilder()
                    .status(PaymentAttemptStatus.FAILED)
                    .idempotencyKey(key)
                    .build();
            when(paymentAttemptRepository.findByIdempotencyKey(key)).thenReturn(Optional.of(existing));
            when(paymentStrategyFactory.getServiceFromPaymentType(PaymentType.VISA)).thenReturn(processor);
            when(processor.processPayment(any(), any(), any()))
                    .thenReturn(new PaymentAttemptResult(PaymentAttemptStatus.SUCCESS, "pi_retry_ok"));
            wireSaveReturnsArg();

            final PaymentEntity result = service.processPayment(order, PaymentType.VISA, tenEur(), key);

            assertThat(result.getStatus()).isEqualTo(PaymentAttemptStatus.SUCCESS);
            assertThat(result.getStripePaymentID()).isEqualTo("pi_retry_ok");
            verify(processor).processPayment(any(), any(), any());
        }

        @Test
        @DisplayName("processor failure (PaymentProcessingException) propagates to caller")
        void processorFailure_propagates() {
            final OrderEntity order = newOrder();
            final String key = "idem-fail";
            when(paymentAttemptRepository.findByIdempotencyKey(key)).thenReturn(Optional.empty());
            when(paymentStrategyFactory.getServiceFromPaymentType(PaymentType.VISA)).thenReturn(processor);
            when(processor.processPayment(any(), any(), any()))
                    .thenThrow(new PaymentProcessingException("Stripe failed", null));
            wireSaveReturnsArg();

            assertThatThrownBy(() -> service.processPayment(order, PaymentType.VISA, tenEur(), key))
                    .isInstanceOf(PaymentProcessingException.class)
                    .hasMessageContaining("Stripe failed");

            // first save (CREATED→PROCESSING) happened; second save (post-processor) did not
            verify(paymentAttemptRepository, times(1)).save(any(PaymentEntity.class));
        }

        @Test
        @DisplayName("processor returns FAILED → entity persisted with FAILED status, PaymentFailedEvent published")
        void failedOutcome_publishesPaymentFailedEvent() {
            final OrderEntity order = newOrder();
            final String key = "idem-failed-result";
            when(paymentAttemptRepository.findByIdempotencyKey(key)).thenReturn(Optional.empty());
            when(paymentStrategyFactory.getServiceFromPaymentType(PaymentType.VISA)).thenReturn(processor);
            when(processor.processPayment(any(), any(), any()))
                    .thenReturn(new PaymentAttemptResult(PaymentAttemptStatus.FAILED, "pi_fail"));
            wireSaveReturnsArg();

            final PaymentEntity result = service.processPayment(order, PaymentType.VISA, tenEur(), key);

            assertThat(result.getStatus()).isEqualTo(PaymentAttemptStatus.FAILED);
            // F2 added publishPaymentOutcomeEvent — FAILED triggers PaymentFailedEvent
            verify(applicationEventPublisher).publishEvent(any(PaymentFailedEvent.class));
            verify(applicationEventPublisher, never()).publishEvent(any(PaymentSucceededEvent.class));
        }

        @Test
        @DisplayName("processor returns SUCCESS → PaymentSucceededEvent published (BUG-070 partial fix)")
        void successOutcome_publishesPaymentSucceededEvent() {
            final OrderEntity order = newOrder();
            final String key = "idem-ok";
            when(paymentAttemptRepository.findByIdempotencyKey(key)).thenReturn(Optional.empty());
            when(paymentStrategyFactory.getServiceFromPaymentType(PaymentType.VISA)).thenReturn(processor);
            when(processor.processPayment(any(), any(), any()))
                    .thenReturn(new PaymentAttemptResult(PaymentAttemptStatus.SUCCESS, "pi_ok"));
            wireSaveReturnsArg();

            service.processPayment(order, PaymentType.VISA, tenEur(), key);

            final ArgumentCaptor<ApplicationEvent> ev = ArgumentCaptor.forClass(ApplicationEvent.class);
            verify(applicationEventPublisher).publishEvent(ev.capture());
            assertThat(ev.getValue()).isInstanceOf(PaymentSucceededEvent.class);
            // Verify the synthetic dto carries order_uuid metadata (so listeners can resolve the order).
            final PaymentSucceededEvent evt = (PaymentSucceededEvent) ev.getValue();
            assertThat(evt.getStripeEvent()).isNotNull();
            assertThat(evt.getStripeEvent().getData().getPaymentIntentPayload().getId()).isEqualTo("pi_ok");
            assertThat(evt.getStripeEvent().getData().getPaymentIntentPayload().getMetadata())
                    .containsEntry("order_uuid", order.getUuid().toString())
                    .containsEntry("idempotency_key", key);
        }

        @Test
        @DisplayName("non-terminal status (PROCESSING) → no event published")
        void processingOutcome_doesNotPublish() {
            final OrderEntity order = newOrder();
            final String key = "idem-proc";
            when(paymentAttemptRepository.findByIdempotencyKey(key)).thenReturn(Optional.empty());
            when(paymentStrategyFactory.getServiceFromPaymentType(PaymentType.VISA)).thenReturn(processor);
            when(processor.processPayment(any(), any(), any()))
                    .thenReturn(new PaymentAttemptResult(PaymentAttemptStatus.PROCESSING, "pi_proc"));
            wireSaveReturnsArg();

            service.processPayment(order, PaymentType.VISA, tenEur(), key);

            verifyNoInteractions(applicationEventPublisher);
        }

        @Test
        @DisplayName("idempotency key passed verbatim to processor (no mutation)")
        void idempotencyKeyForwardedVerbatim() {
            final OrderEntity order = newOrder();
            final String key = "idem-verbatim-XYZ-123";
            when(paymentAttemptRepository.findByIdempotencyKey(key)).thenReturn(Optional.empty());
            when(paymentStrategyFactory.getServiceFromPaymentType(PaymentType.MASTERCARD)).thenReturn(processor);
            when(processor.processPayment(any(), any(), eq(key)))
                    .thenReturn(PaymentDtoFixtures.aSuccessfulAttemptResult());
            wireSaveReturnsArg();

            service.processPayment(order, PaymentType.MASTERCARD, tenEur(), key);

            verify(processor).processPayment(eq(order.getUuid()), any(Money.class), eq(key));
        }

        @Test
        @DisplayName("BUG-071: null idempotencyKey is not guarded — passes null to repository (PIN current behavior)")
        void processPayment_nullIdempotencyKey_notGuarded_pin() {
            // PIN: BUG-071 still open. Service should reject null idempotencyKey at entry; instead it
            // calls repository.findByIdempotencyKey(null) and propagates null further.
            final OrderEntity order = newOrder();
            when(paymentAttemptRepository.findByIdempotencyKey(null)).thenReturn(Optional.empty());
            when(paymentStrategyFactory.getServiceFromPaymentType(PaymentType.VISA)).thenReturn(processor);
            when(processor.processPayment(any(), any(), any()))
                    .thenReturn(PaymentDtoFixtures.aSuccessfulAttemptResult());
            wireSaveReturnsArg();

            // current behavior: silently proceeds with null key — no IllegalArgumentException
            service.processPayment(order, PaymentType.VISA, tenEur(), null);

            verify(paymentAttemptRepository).findByIdempotencyKey(null);
            // documents the gap: a defensive guard would short-circuit here with a domain exception.
        }

        @Test
        @DisplayName("BUG-072: null order causes NPE on order.getUuid (PIN current behavior)")
        void processPayment_nullOrder_npe_pin() {
            // PIN: BUG-072 still open. Service does not validate order != null at entry.
            // The NPE surfaces from inside the entity-builder / processor call rather than as a
            // clean domain-level IllegalArgumentException.
            assertThatThrownBy(() ->
                    service.processPayment(null, PaymentType.VISA, tenEur(), "idem-null-order"))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("refund")
    class Refund {

        @Test
        @DisplayName("happy path: derives refund key, delegates to processor, persists & returns")
        void happyPath() {
            final OrderEntity order = newOrder();
            final String originalKey = "idem-original";
            final String derivedRefundKey = "idem-original-refund-derived";
            final PaymentEntity originalAttempt = PaymentEntityBuilder.aValidPaymentBuilder()
                    .idempotencyKey(originalKey)
                    .stripePaymentID("pi_original")
                    .build();
            when(paymentAttemptRepository.findByIdempotencyKey(originalKey))
                    .thenReturn(Optional.of(originalAttempt));
            when(idempotencyKeyService.generateKey(eq(order.getUuid().toString()), anyList()))
                    .thenReturn(derivedRefundKey);
            when(paymentStrategyFactory.getServiceFromPaymentType(PaymentType.VISA)).thenReturn(processor);
            when(processor.refund(order.getUuid(), tenEur(), derivedRefundKey, "pi_original"))
                    .thenReturn(new PaymentAttemptResult(PaymentAttemptStatus.SUCCESS, "re_123"));
            wireSaveReturnsArg();

            final PaymentEntity result = service.refund(order, PaymentType.VISA, tenEur(), originalKey);

            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(PaymentAttemptStatus.SUCCESS);
            assertThat(result.getStripePaymentID()).isEqualTo("re_123");
            assertThat(result.getTransactionType()).isEqualTo(TransactionType.REFUND);
            assertThat(result.getIdempotencyKey()).isEqualTo(derivedRefundKey);
            verify(processor).refund(order.getUuid(), tenEur(), derivedRefundKey, "pi_original");
        }

        @Test
        @DisplayName("derived idempotency key is built from orderUuid + ['refund', originalKey]")
        void keyDerivation() {
            final OrderEntity order = newOrder();
            final String originalKey = "idem-key-deriv";
            final PaymentEntity originalAttempt = PaymentEntityBuilder.aValidPaymentBuilder()
                    .idempotencyKey(originalKey).stripePaymentID("pi_xyz").build();

            when(paymentAttemptRepository.findByIdempotencyKey(originalKey))
                    .thenReturn(Optional.of(originalAttempt));
            when(idempotencyKeyService.generateKey(anyString(), anyList()))
                    .thenReturn("derived-key");
            when(paymentStrategyFactory.getServiceFromPaymentType(PaymentType.VISA)).thenReturn(processor);
            when(processor.refund(any(), any(), any(), any()))
                    .thenReturn(new PaymentAttemptResult(PaymentAttemptStatus.SUCCESS, "re_abc"));
            wireSaveReturnsArg();

            service.refund(order, PaymentType.VISA, tenEur(), originalKey);

            final ArgumentCaptor<String> orderUuidCap = ArgumentCaptor.forClass(String.class);
            @SuppressWarnings("unchecked")
            final ArgumentCaptor<List<String>> contextCap = ArgumentCaptor.forClass(List.class);
            verify(idempotencyKeyService).generateKey(orderUuidCap.capture(), contextCap.capture());

            assertThat(orderUuidCap.getValue()).isEqualTo(order.getUuid().toString());
            assertThat(contextCap.getValue()).containsExactly("refund", originalKey);
        }

        @Test
        @DisplayName("payment not found for original key → throws PaymentNotFoundException")
        void paymentNotFound() {
            final OrderEntity order = newOrder();
            when(paymentAttemptRepository.findByIdempotencyKey("idem-missing"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.refund(order, PaymentType.VISA, tenEur(), "idem-missing"))
                    .isInstanceOf(PaymentNotFoundException.class)
                    .hasMessageContaining("idem-missing");

            verify(paymentAttemptRepository, never()).save(any());
            verifyNoInteractions(paymentStrategyFactory, processor, idempotencyKeyService);
        }

        @Test
        @DisplayName("processor failure (PaymentProcessingException) propagates to caller")
        void processorFailure() {
            final OrderEntity order = newOrder();
            final String originalKey = "idem-original-fail";
            final PaymentEntity originalAttempt = PaymentEntityBuilder.aValidPaymentBuilder()
                    .idempotencyKey(originalKey).stripePaymentID("pi_x").build();
            when(paymentAttemptRepository.findByIdempotencyKey(originalKey))
                    .thenReturn(Optional.of(originalAttempt));
            when(idempotencyKeyService.generateKey(anyString(), anyList()))
                    .thenReturn("idem-refund-fail");
            when(paymentStrategyFactory.getServiceFromPaymentType(PaymentType.VISA)).thenReturn(processor);
            when(processor.refund(any(), any(), any(), any()))
                    .thenThrow(new PaymentProcessingException("Stripe refund failed", null));
            wireSaveReturnsArg();

            assertThatThrownBy(() -> service.refund(order, PaymentType.VISA, tenEur(), originalKey))
                    .isInstanceOf(PaymentProcessingException.class)
                    .hasMessageContaining("Stripe refund failed");
        }

        @Test
        @DisplayName("processor returns FAILED → entity persisted with FAILED status (no exception)")
        void processorFailedResult() {
            final OrderEntity order = newOrder();
            final String originalKey = "idem-original-failedresult";
            final PaymentEntity originalAttempt = PaymentEntityBuilder.aValidPaymentBuilder()
                    .idempotencyKey(originalKey).stripePaymentID("pi_x").build();
            when(paymentAttemptRepository.findByIdempotencyKey(originalKey))
                    .thenReturn(Optional.of(originalAttempt));
            when(idempotencyKeyService.generateKey(anyString(), anyList()))
                    .thenReturn("idem-refund-failed-result");
            when(paymentStrategyFactory.getServiceFromPaymentType(PaymentType.VISA)).thenReturn(processor);
            when(processor.refund(any(), any(), any(), any()))
                    .thenReturn(new PaymentAttemptResult(PaymentAttemptStatus.FAILED, "re_failed"));
            wireSaveReturnsArg();

            final PaymentEntity result = service.refund(order, PaymentType.VISA, tenEur(), originalKey);
            assertThat(result.getStatus()).isEqualTo(PaymentAttemptStatus.FAILED);
            assertThat(result.getStripePaymentID()).isEqualTo("re_failed");
        }

        @Test
        @DisplayName("uses original payment's stripePaymentID for refund (not a new id)")
        void usesOriginalStripePaymentId() {
            final OrderEntity order = newOrder();
            final String originalKey = "idem-with-id";
            final PaymentEntity originalAttempt = PaymentEntityBuilder.aValidPaymentBuilder()
                    .idempotencyKey(originalKey)
                    .stripePaymentID("pi_must_use_this")
                    .build();
            when(paymentAttemptRepository.findByIdempotencyKey(originalKey))
                    .thenReturn(Optional.of(originalAttempt));
            when(idempotencyKeyService.generateKey(anyString(), anyList())).thenReturn("idem-r");
            when(paymentStrategyFactory.getServiceFromPaymentType(PaymentType.VISA)).thenReturn(processor);
            when(processor.refund(any(), any(), any(), any()))
                    .thenReturn(new PaymentAttemptResult(PaymentAttemptStatus.SUCCESS, "re_ok"));
            wireSaveReturnsArg();

            service.refund(order, PaymentType.VISA, tenEur(), originalKey);

            verify(processor).refund(eq(order.getUuid()), eq(tenEur()), eq("idem-r"), eq("pi_must_use_this"));
        }
    }

    @Nested
    @DisplayName("documented bug pins")
    class DocumentedFindings {

        @Test
        @DisplayName("BUG-070: refund() does NOT publish PaymentRefundedEvent (PIN — webhook-only)")
        void refund_doesNotPublishRefundedEvent() {
            // PIN: BUG-070. F2 added publishPaymentOutcomeEvent only to processPayment().
            // refund() still does not publish PaymentRefundedEvent; that surface remains webhook-only.
            final OrderEntity order = newOrder();
            final String originalKey = "idem-no-refund-event";
            final PaymentEntity originalAttempt = PaymentEntityBuilder.aValidPaymentBuilder()
                    .idempotencyKey(originalKey).stripePaymentID("pi_x").build();
            when(paymentAttemptRepository.findByIdempotencyKey(originalKey))
                    .thenReturn(Optional.of(originalAttempt));
            when(idempotencyKeyService.generateKey(anyString(), anyList())).thenReturn("idem-r");
            when(paymentStrategyFactory.getServiceFromPaymentType(PaymentType.VISA)).thenReturn(processor);
            when(processor.refund(any(), any(), any(), any()))
                    .thenReturn(new PaymentAttemptResult(PaymentAttemptStatus.SUCCESS, "re_ok"));
            wireSaveReturnsArg();

            service.refund(order, PaymentType.VISA, tenEur(), originalKey);

            verifyNoInteractions(applicationEventPublisher);
        }
    }
}
