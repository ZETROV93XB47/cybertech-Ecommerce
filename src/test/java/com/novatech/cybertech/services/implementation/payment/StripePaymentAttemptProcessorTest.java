package com.novatech.cybertech.services.implementation.payment;

import com.novatech.cybertech.dto.data.PaymentAttemptResult;
import com.novatech.cybertech.entities.enums.PaymentAttemptStatus;
import com.novatech.cybertech.entities.valueObjects.CurrencyCode;
import com.novatech.cybertech.entities.valueObjects.Money;
import com.novatech.cybertech.exceptions.PaymentProcessingException;
import com.novatech.cybertech.services.implementation.StripePaymentAttemptProcessor;
import com.stripe.exception.CardException;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link StripePaymentAttemptProcessor}.
 *
 * <p>The Stripe SDK exposes static factory methods ({@code PaymentIntent.create},
 * {@code Refund.create}) for building remote API calls. We rely on
 * {@link Mockito#mockStatic(Class)} (mockito-inline mock-maker) to intercept
 * those calls without hitting the network. This processor is otherwise
 * dependency-free, so we instantiate it directly and inject the
 * {@code @Value("${stripe.payment-method:}")} default via
 * {@link ReflectionTestUtils} when needed.</p>
 *
 * <p>Coverage focus: status mapping (succeeded / requires_payment_method /
 * canceled / processing fallback), refund status mapping (succeeded / failed /
 * canceled / fallback), {@link StripeException} translation into
 * {@link PaymentProcessingException}, and the BUG-075/076/077 contract around
 * the configurable {@code defaultPaymentMethod}.</p>
 */
@ExtendWith(MockitoExtension.class)
class StripePaymentAttemptProcessorTest {

    private StripePaymentAttemptProcessor processor;

    private UUID orderUuid;
    private String idempotencyKey;
    private Money amount;

    @BeforeEach
    void setUp() {
        processor = new StripePaymentAttemptProcessor();
        // default behaviour: no payment-method override (BUG-075 default)
        ReflectionTestUtils.setField(processor, "defaultPaymentMethod", "");

        orderUuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
        idempotencyKey = "idem-key-001";
        amount = new Money(new BigDecimal("12.34"), CurrencyCode.EUR);
    }

    private PaymentIntent stubPaymentIntent(String id, String status) {
        PaymentIntent intent = mock(PaymentIntent.class);
        when(intent.getId()).thenReturn(id);
        when(intent.getStatus()).thenReturn(status);
        return intent;
    }

    private Refund stubRefund(String id, String status) {
        Refund refund = mock(Refund.class);
        when(refund.getId()).thenReturn(id);
        when(refund.getStatus()).thenReturn(status);
        return refund;
    }

    /**
     * Builds a concrete {@link StripeException} (the parent class is abstract)
     * so we can drive the SDK error branch.
     */
    private StripeException stripeError(String message, String code) {
        return new CardException(message, "req_test", code, null, null, null, 402, null);
    }

    // ---------------------------------------------------------------------
    // processPayment
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("processPayment")
    class ProcessPayment {

        @Test
        @DisplayName("succeeded Stripe status maps to PaymentAttemptStatus.SUCCESS and returns intent id")
        void mapsSucceededToSuccess() {
            try (MockedStatic<PaymentIntent> piMock = Mockito.mockStatic(PaymentIntent.class)) {
                PaymentIntent intent = stubPaymentIntent("pi_succeeded_1", "succeeded");
                piMock.when(() -> PaymentIntent.create(
                                any(PaymentIntentCreateParams.class), any(RequestOptions.class)))
                        .thenReturn(intent);

                PaymentAttemptResult result = processor.processPayment(orderUuid, amount, idempotencyKey);

                assertThat(result).isNotNull();
                assertThat(result.status()).isEqualTo(PaymentAttemptStatus.SUCCESS);
                assertThat(result.stripePaymentID()).isEqualTo("pi_succeeded_1");
            }
        }

        @Test
        @DisplayName("requires_payment_method maps to FAILED")
        void mapsRequiresPaymentMethodToFailed() {
            try (MockedStatic<PaymentIntent> piMock = Mockito.mockStatic(PaymentIntent.class)) {
                PaymentIntent intent = stubPaymentIntent("pi_failed_1", "requires_payment_method");
                piMock.when(() -> PaymentIntent.create(
                                any(PaymentIntentCreateParams.class), any(RequestOptions.class)))
                        .thenReturn(intent);

                PaymentAttemptResult result = processor.processPayment(orderUuid, amount, idempotencyKey);

                assertThat(result.status()).isEqualTo(PaymentAttemptStatus.FAILED);
                assertThat(result.stripePaymentID()).isEqualTo("pi_failed_1");
            }
        }

        @Test
        @DisplayName("canceled Stripe status also maps to FAILED (sharing the requires_payment_method branch)")
        void mapsCanceledToFailed() {
            try (MockedStatic<PaymentIntent> piMock = Mockito.mockStatic(PaymentIntent.class)) {
                PaymentIntent intent = stubPaymentIntent("pi_cancelled_1", "canceled");
                piMock.when(() -> PaymentIntent.create(
                                any(PaymentIntentCreateParams.class), any(RequestOptions.class)))
                        .thenReturn(intent);

                PaymentAttemptResult result = processor.processPayment(orderUuid, amount, idempotencyKey);

                assertThat(result.status()).isEqualTo(PaymentAttemptStatus.FAILED);
            }
        }

        @Test
        @DisplayName("any other Stripe status (e.g. requires_action) falls back to PROCESSING")
        void mapsUnknownStatusToProcessing() {
            try (MockedStatic<PaymentIntent> piMock = Mockito.mockStatic(PaymentIntent.class)) {
                PaymentIntent intent = stubPaymentIntent("pi_processing_1", "requires_action");
                piMock.when(() -> PaymentIntent.create(
                                any(PaymentIntentCreateParams.class), any(RequestOptions.class)))
                        .thenReturn(intent);

                PaymentAttemptResult result = processor.processPayment(orderUuid, amount, idempotencyKey);

                assertThat(result.status()).isEqualTo(PaymentAttemptStatus.PROCESSING);
                assertThat(result.stripePaymentID()).isEqualTo("pi_processing_1");
            }
        }

        @Test
        @DisplayName("StripeException is wrapped in PaymentProcessingException with the order UUID in the message")
        void wrapsStripeExceptionInPaymentProcessingException() {
            try (MockedStatic<PaymentIntent> piMock = Mockito.mockStatic(PaymentIntent.class)) {
                StripeException boom = stripeError("Card declined", "card_declined");
                piMock.when(() -> PaymentIntent.create(
                                any(PaymentIntentCreateParams.class), any(RequestOptions.class)))
                        .thenThrow(boom);

                assertThatThrownBy(() -> processor.processPayment(orderUuid, amount, idempotencyKey))
                        .isInstanceOf(PaymentProcessingException.class)
                        .hasMessageContaining(orderUuid.toString());
            }
        }

        @Test
        @DisplayName("amount is converted to minor units (cents) and currency lower-cased on the Stripe params")
        void buildsParamsWithMinorUnitsAndLowercaseCurrency() {
            try (MockedStatic<PaymentIntent> piMock = Mockito.mockStatic(PaymentIntent.class)) {
                ArgumentCaptor<PaymentIntentCreateParams> captor =
                        ArgumentCaptor.forClass(PaymentIntentCreateParams.class);
                ArgumentCaptor<RequestOptions> optionsCaptor =
                        ArgumentCaptor.forClass(RequestOptions.class);

                PaymentIntent intent = stubPaymentIntent("pi_capture_1", "succeeded");
                piMock.when(() -> PaymentIntent.create(
                                any(PaymentIntentCreateParams.class), any(RequestOptions.class)))
                        .thenReturn(intent);

                Money usdAmount = new Money(new BigDecimal("10.00"), CurrencyCode.USD);
                processor.processPayment(orderUuid, usdAmount, idempotencyKey);

                piMock.verify(() -> PaymentIntent.create(captor.capture(), optionsCaptor.capture()));

                PaymentIntentCreateParams sent = captor.getValue();
                assertThat(sent.getAmount()).isEqualTo(1000L); // 10.00 USD -> 1000 cents
                assertThat(sent.getCurrency()).isEqualTo("usd");
                assertThat(sent.getConfirm()).isTrue();
                // metadata must include both order_uuid and idempotency_key
                assertThat(sent.getMetadata()).containsEntry("order_uuid", orderUuid.toString());
                assertThat(sent.getMetadata()).containsEntry("idempotency_key", idempotencyKey);

                RequestOptions sentOptions = optionsCaptor.getValue();
                assertThat(sentOptions.getIdempotencyKey()).isEqualTo(idempotencyKey);
            }
        }

        @Test
        @DisplayName("BUG-075: when stripe.payment-method is configured, it is forwarded as paymentMethod")
        void forwardsConfiguredPaymentMethodWhenPresent() {
            ReflectionTestUtils.setField(processor, "defaultPaymentMethod", "pm_card_visa");

            try (MockedStatic<PaymentIntent> piMock = Mockito.mockStatic(PaymentIntent.class)) {
                ArgumentCaptor<PaymentIntentCreateParams> captor =
                        ArgumentCaptor.forClass(PaymentIntentCreateParams.class);

                PaymentIntent intent = stubPaymentIntent("pi_pm_1", "succeeded");
                piMock.when(() -> PaymentIntent.create(
                                any(PaymentIntentCreateParams.class), any(RequestOptions.class)))
                        .thenReturn(intent);

                processor.processPayment(orderUuid, amount, idempotencyKey);

                piMock.verify(() -> PaymentIntent.create(captor.capture(), any(RequestOptions.class)));
                assertThat(captor.getValue().getPaymentMethod()).isEqualTo("pm_card_visa");
            }
        }

        @Test
        @DisplayName("BUG-075: blank stripe.payment-method does NOT set paymentMethod on the params")
        void doesNotSetPaymentMethodWhenBlank() {
            ReflectionTestUtils.setField(processor, "defaultPaymentMethod", "   ");

            try (MockedStatic<PaymentIntent> piMock = Mockito.mockStatic(PaymentIntent.class)) {
                ArgumentCaptor<PaymentIntentCreateParams> captor =
                        ArgumentCaptor.forClass(PaymentIntentCreateParams.class);

                PaymentIntent intent = stubPaymentIntent("pi_pm_blank", "succeeded");
                piMock.when(() -> PaymentIntent.create(
                                any(PaymentIntentCreateParams.class), any(RequestOptions.class)))
                        .thenReturn(intent);

                processor.processPayment(orderUuid, amount, idempotencyKey);

                piMock.verify(() -> PaymentIntent.create(captor.capture(), any(RequestOptions.class)));
                assertThat(captor.getValue().getPaymentMethod()).isNull();
            }
        }

        @Test
        @DisplayName("null defaultPaymentMethod (unset @Value) leaves paymentMethod unset")
        void doesNotSetPaymentMethodWhenNull() {
            ReflectionTestUtils.setField(processor, "defaultPaymentMethod", null);

            try (MockedStatic<PaymentIntent> piMock = Mockito.mockStatic(PaymentIntent.class)) {
                ArgumentCaptor<PaymentIntentCreateParams> captor =
                        ArgumentCaptor.forClass(PaymentIntentCreateParams.class);

                PaymentIntent intent = stubPaymentIntent("pi_pm_null", "succeeded");
                piMock.when(() -> PaymentIntent.create(
                                any(PaymentIntentCreateParams.class), any(RequestOptions.class)))
                        .thenReturn(intent);

                processor.processPayment(orderUuid, amount, idempotencyKey);

                piMock.verify(() -> PaymentIntent.create(captor.capture(), any(RequestOptions.class)));
                assertThat(captor.getValue().getPaymentMethod()).isNull();
            }
        }
    }

    // ---------------------------------------------------------------------
    // refund
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("refund")
    class RefundCases {

        @Test
        @DisplayName("succeeded refund maps to PaymentAttemptStatus.SUCCESS")
        void succeededRefundMapsToSuccess() {
            try (MockedStatic<Refund> refundMock = Mockito.mockStatic(Refund.class)) {
                Refund stub = stubRefund("re_succeeded_1", "succeeded");
                refundMock.when(() -> Refund.create(
                                any(RefundCreateParams.class), any(RequestOptions.class)))
                        .thenReturn(stub);

                PaymentAttemptResult result = processor.refund(
                        orderUuid, amount, idempotencyKey, "pi_to_refund");

                assertThat(result.status()).isEqualTo(PaymentAttemptStatus.SUCCESS);
                assertThat(result.stripePaymentID()).isEqualTo("re_succeeded_1");
            }
        }

        @Test
        @DisplayName("failed refund maps to FAILED")
        void failedRefundMapsToFailed() {
            try (MockedStatic<Refund> refundMock = Mockito.mockStatic(Refund.class)) {
                Refund stub = stubRefund("re_failed_1", "failed");
                refundMock.when(() -> Refund.create(
                                any(RefundCreateParams.class), any(RequestOptions.class)))
                        .thenReturn(stub);

                PaymentAttemptResult result = processor.refund(
                        orderUuid, amount, idempotencyKey, "pi_to_refund");

                assertThat(result.status()).isEqualTo(PaymentAttemptStatus.FAILED);
            }
        }

        @Test
        @DisplayName("canceled refund maps to CANCELED")
        void canceledRefundMapsToCanceled() {
            try (MockedStatic<Refund> refundMock = Mockito.mockStatic(Refund.class)) {
                Refund stub = stubRefund("re_canceled_1", "canceled");
                refundMock.when(() -> Refund.create(
                                any(RefundCreateParams.class), any(RequestOptions.class)))
                        .thenReturn(stub);

                PaymentAttemptResult result = processor.refund(
                        orderUuid, amount, idempotencyKey, "pi_to_refund");

                assertThat(result.status()).isEqualTo(PaymentAttemptStatus.CANCELED);
            }
        }

        @Test
        @DisplayName("any other refund status (e.g. pending) falls back to PROCESSING")
        void unknownRefundStatusMapsToProcessing() {
            try (MockedStatic<Refund> refundMock = Mockito.mockStatic(Refund.class)) {
                Refund stub = stubRefund("re_pending_1", "pending");
                refundMock.when(() -> Refund.create(
                                any(RefundCreateParams.class), any(RequestOptions.class)))
                        .thenReturn(stub);

                PaymentAttemptResult result = processor.refund(
                        orderUuid, amount, idempotencyKey, "pi_to_refund");

                assertThat(result.status()).isEqualTo(PaymentAttemptStatus.PROCESSING);
            }
        }

        @Test
        @DisplayName("BUG-077: refund forwards paymentIntent + amount in minor units + orderUuid metadata")
        void buildsRefundParamsCorrectly() {
            try (MockedStatic<Refund> refundMock = Mockito.mockStatic(Refund.class)) {
                ArgumentCaptor<RefundCreateParams> captor =
                        ArgumentCaptor.forClass(RefundCreateParams.class);
                ArgumentCaptor<RequestOptions> optsCaptor =
                        ArgumentCaptor.forClass(RequestOptions.class);

                Refund stub = stubRefund("re_capture_1", "succeeded");
                refundMock.when(() -> Refund.create(
                                any(RefundCreateParams.class), any(RequestOptions.class)))
                        .thenReturn(stub);

                Money refundAmount = new Money(new BigDecimal("7.50"), CurrencyCode.EUR);
                processor.refund(orderUuid, refundAmount, idempotencyKey, "pi_target");

                refundMock.verify(() -> Refund.create(captor.capture(), optsCaptor.capture()));

                RefundCreateParams sent = captor.getValue();
                assertThat(sent.getPaymentIntent()).isEqualTo("pi_target");
                assertThat(sent.getAmount()).isEqualTo(750L);
                // RefundCreateParams.getMetadata() returns Object (Map at runtime)
                @SuppressWarnings("unchecked")
                java.util.Map<String, String> meta =
                        (java.util.Map<String, String>) sent.getMetadata();
                assertThat(meta).containsEntry("orderUuid", orderUuid.toString());

                assertThat(optsCaptor.getValue().getIdempotencyKey()).isEqualTo(idempotencyKey);
            }
        }

        @Test
        @DisplayName("StripeException during refund is wrapped in PaymentProcessingException with the order UUID")
        void wrapsRefundStripeExceptionInPaymentProcessingException() {
            try (MockedStatic<Refund> refundMock = Mockito.mockStatic(Refund.class)) {
                StripeException boom = stripeError("charge already refunded", "charge_already_refunded");
                refundMock.when(() -> Refund.create(
                                any(RefundCreateParams.class), any(RequestOptions.class)))
                        .thenThrow(boom);

                assertThatThrownBy(() -> processor.refund(
                        orderUuid, amount, idempotencyKey, "pi_already_refunded"))
                        .isInstanceOf(PaymentProcessingException.class)
                        .hasMessageContaining(orderUuid.toString());
            }
        }
    }
}
