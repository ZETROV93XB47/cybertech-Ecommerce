package com.novatech.cybertech.services.implementation.payment;

import com.novatech.cybertech.dto.data.StripeCardDetailsDto;
import com.novatech.cybertech.dto.data.StripeSetupIntentDto;
import com.novatech.cybertech.exceptions.InvalidCardSetupException;
import com.novatech.cybertech.exceptions.PaymentProcessingException;
import com.novatech.cybertech.services.implementation.StripeCardGatewayImp;
import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.InvalidRequestException;
import com.stripe.model.Customer;
import com.stripe.model.PaymentMethod;
import com.stripe.model.SetupIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.CustomerUpdateParams;
import com.stripe.param.PaymentMethodAttachParams;
import com.stripe.param.PaymentMethodUpdateParams;
import com.stripe.param.SetupIntentCreateParams;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link StripeCardGatewayImp}: request parameters sent to Stripe and translation
 * of the SDK's results and errors. The SDK's static calls are intercepted with
 * {@link Mockito#mockStatic(Class)}, as in {@code StripePaymentAttemptProcessorTest}.
 */
class StripeCardGatewayImpTest {

    private final StripeCardGatewayImp gateway = new StripeCardGatewayImp();

    @Test
    @DisplayName("createCustomer: email, name, userUuid metadata, idempotency key per user")
    void createCustomerSendsIdentityAndIdempotencyKey() {
        final UUID userUuid = UUID.randomUUID();
        try (MockedStatic<Customer> customerMock = Mockito.mockStatic(Customer.class)) {
            final Customer customer = mock(Customer.class);
            when(customer.getId()).thenReturn("cus_1");
            customerMock.when(() -> Customer.create(any(CustomerCreateParams.class), any(RequestOptions.class)))
                    .thenReturn(customer);

            assertThat(gateway.createCustomer(userUuid, "jane@example.com", "Jane Doe")).isEqualTo("cus_1");

            final ArgumentCaptor<CustomerCreateParams> params = ArgumentCaptor.forClass(CustomerCreateParams.class);
            final ArgumentCaptor<RequestOptions> options = ArgumentCaptor.forClass(RequestOptions.class);
            customerMock.verify(() -> Customer.create(params.capture(), options.capture()));
            assertThat(params.getValue().getEmail()).isEqualTo("jane@example.com");
            assertThat(params.getValue().getName()).isEqualTo("Jane Doe");
            assertThat(params.getValue().getMetadata()).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP).containsEntry("userUuid", userUuid.toString());
            assertThat(options.getValue().getIdempotencyKey()).isEqualTo("customer-" + userUuid);
        }
    }

    @Test
    @DisplayName("createSetupIntent: customer, off-session usage, no redirect-based method")
    void createSetupIntentForOffSessionCard() {
        try (MockedStatic<SetupIntent> setupMock = Mockito.mockStatic(SetupIntent.class)) {
            final SetupIntent intent = setupIntent("seti_1", "cus_1", "requires_payment_method", null);
            setupMock.when(() -> SetupIntent.create(any(SetupIntentCreateParams.class))).thenReturn(intent);

            final StripeSetupIntentDto dto = gateway.createSetupIntent("cus_1");

            assertThat(dto.id()).isEqualTo("seti_1");
            assertThat(dto.clientSecret()).isEqualTo("seti_1_secret");
            final ArgumentCaptor<SetupIntentCreateParams> params = ArgumentCaptor.forClass(SetupIntentCreateParams.class);
            setupMock.verify(() -> SetupIntent.create(params.capture()));
            assertThat(params.getValue().getCustomer()).isEqualTo("cus_1");
            assertThat(params.getValue().getUsage()).isEqualTo(SetupIntentCreateParams.Usage.OFF_SESSION);
            assertThat(params.getValue().getPaymentMethodTypes()).isNull();
            assertThat(params.getValue().getAutomaticPaymentMethods().getAllowRedirects())
                    .isEqualTo(SetupIntentCreateParams.AutomaticPaymentMethods.AllowRedirects.NEVER);
        }
    }

    @Test
    @DisplayName("retrieveSetupIntent: customer, status and saved PaymentMethod")
    void retrieveSetupIntent() {
        try (MockedStatic<SetupIntent> setupMock = Mockito.mockStatic(SetupIntent.class)) {
            final SetupIntent intent = setupIntent("seti_2", "cus_2", "succeeded", "pm_2");
            setupMock.when(() -> SetupIntent.retrieve("seti_2")).thenReturn(intent);

            assertThat(gateway.retrieveSetupIntent("seti_2"))
                    .isEqualTo(new StripeSetupIntentDto("seti_2", "seti_2_secret", "cus_2", "succeeded", "pm_2"));
        }
    }

    @Test
    @DisplayName("retrievePaymentMethod: brand, last4, expiry, holder name; never more of the card")
    void retrievePaymentMethodReadsDisplayDataOnly() {
        try (MockedStatic<PaymentMethod> pmMock = Mockito.mockStatic(PaymentMethod.class)) {
            final PaymentMethod paymentMethod = cardPaymentMethod("pm_3", "cus_3", "visa", "4242", 12L, 2031L, "J DOE");
            pmMock.when(() -> PaymentMethod.retrieve("pm_3")).thenReturn(paymentMethod);

            assertThat(gateway.retrievePaymentMethod("pm_3"))
                    .isEqualTo(new StripeCardDetailsDto("pm_3", "cus_3", "visa", "4242", 12, 2031, "J DOE"));
        }
    }

    @Test
    @DisplayName("retrievePaymentMethod: a non-card PaymentMethod is an invalid setup")
    void nonCardPaymentMethodIsRejected() {
        try (MockedStatic<PaymentMethod> pmMock = Mockito.mockStatic(PaymentMethod.class)) {
            final PaymentMethod paymentMethod = mock(PaymentMethod.class);
            when(paymentMethod.getId()).thenReturn("pm_sepa");
            pmMock.when(() -> PaymentMethod.retrieve("pm_sepa")).thenReturn(paymentMethod);

            assertThatThrownBy(() -> gateway.retrievePaymentMethod("pm_sepa"))
                    .isInstanceOf(InvalidCardSetupException.class);
        }
    }

    @Test
    @DisplayName("attach / update / detach act on the retrieved PaymentMethod")
    void attachUpdateDetach() throws Exception {
        try (MockedStatic<PaymentMethod> pmMock = Mockito.mockStatic(PaymentMethod.class)) {
            final PaymentMethod paymentMethod = mock(PaymentMethod.class);
            pmMock.when(() -> PaymentMethod.retrieve("pm_4")).thenReturn(paymentMethod);

            gateway.attachPaymentMethod("pm_4", "cus_4");
            gateway.updatePaymentMethod("pm_4", "New Holder", 6, 2032);
            gateway.detachPaymentMethod("pm_4");

            final ArgumentCaptor<PaymentMethodAttachParams> attach = ArgumentCaptor.forClass(PaymentMethodAttachParams.class);
            verify(paymentMethod).attach(attach.capture());
            assertThat(attach.getValue().getCustomer()).isEqualTo("cus_4");
            final ArgumentCaptor<PaymentMethodUpdateParams> update = ArgumentCaptor.forClass(PaymentMethodUpdateParams.class);
            verify(paymentMethod).update(update.capture());
            assertThat(update.getValue().getBillingDetails().getName()).isEqualTo("New Holder");
            assertThat(update.getValue().getCard().getExpMonth()).isEqualTo(6L);
            assertThat(update.getValue().getCard().getExpYear()).isEqualTo(2032L);
            verify(paymentMethod).detach();
        }
    }

    @Test
    @DisplayName("setDefaultPaymentMethod sets invoice_settings.default_payment_method")
    void setDefaultPaymentMethod() throws Exception {
        try (MockedStatic<Customer> customerMock = Mockito.mockStatic(Customer.class)) {
            final Customer customer = mock(Customer.class);
            customerMock.when(() -> Customer.retrieve("cus_5")).thenReturn(customer);

            gateway.setDefaultPaymentMethod("cus_5", "pm_5");

            final ArgumentCaptor<CustomerUpdateParams> params = ArgumentCaptor.forClass(CustomerUpdateParams.class);
            verify(customer).update(params.capture());
            assertThat(params.getValue().getInvoiceSettings().getDefaultPaymentMethod()).isEqualTo("pm_5");
        }
    }

    @Test
    @DisplayName("unknown id (InvalidRequestException) becomes InvalidCardSetupException (400)")
    void invalidRequestBecomesInvalidCardSetup() {
        try (MockedStatic<SetupIntent> setupMock = Mockito.mockStatic(SetupIntent.class)) {
            setupMock.when(() -> SetupIntent.retrieve("seti_unknown")).thenThrow(
                    new InvalidRequestException("No such setupintent", "id", "req_1", "resource_missing", 404, null));

            assertThatThrownBy(() -> gateway.retrieveSetupIntent("seti_unknown"))
                    .isInstanceOf(InvalidCardSetupException.class)
                    .hasMessageContaining("resource_missing");
        }
    }

    @Test
    @DisplayName("Stripe outage becomes PaymentProcessingException")
    void outageBecomesPaymentProcessingException() {
        try (MockedStatic<SetupIntent> setupMock = Mockito.mockStatic(SetupIntent.class)) {
            setupMock.when(() -> SetupIntent.create(any(SetupIntentCreateParams.class)))
                    .thenThrow(new ApiConnectionException("Stripe unreachable"));

            assertThatThrownBy(() -> gateway.createSetupIntent("cus_1"))
                    .isInstanceOf(PaymentProcessingException.class);
        }
    }

    private static SetupIntent setupIntent(final String id, final String customer, final String status, final String paymentMethod) {
        final SetupIntent intent = mock(SetupIntent.class);
        when(intent.getId()).thenReturn(id);
        when(intent.getClientSecret()).thenReturn(id + "_secret");
        when(intent.getCustomer()).thenReturn(customer);
        when(intent.getStatus()).thenReturn(status);
        when(intent.getPaymentMethod()).thenReturn(paymentMethod);
        return intent;
    }

    private static PaymentMethod cardPaymentMethod(final String id, final String customer, final String brand,
                                                   final String last4, final Long expMonth, final Long expYear,
                                                   final String holder) {
        final PaymentMethod.Card card = mock(PaymentMethod.Card.class);
        when(card.getBrand()).thenReturn(brand);
        when(card.getLast4()).thenReturn(last4);
        when(card.getExpMonth()).thenReturn(expMonth);
        when(card.getExpYear()).thenReturn(expYear);
        final PaymentMethod.BillingDetails billing = mock(PaymentMethod.BillingDetails.class);
        when(billing.getName()).thenReturn(holder);
        final PaymentMethod paymentMethod = mock(PaymentMethod.class);
        when(paymentMethod.getId()).thenReturn(id);
        when(paymentMethod.getCustomer()).thenReturn(customer);
        when(paymentMethod.getCard()).thenReturn(card);
        when(paymentMethod.getBillingDetails()).thenReturn(billing);
        return paymentMethod;
    }
}
