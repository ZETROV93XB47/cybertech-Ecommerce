package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.dto.data.StripeCardDetailsDto;
import com.novatech.cybertech.dto.data.StripeSetupIntentDto;
import com.novatech.cybertech.exceptions.InvalidCardSetupException;
import com.novatech.cybertech.exceptions.PaymentProcessingException;
import com.novatech.cybertech.services.core.StripeCardGateway;
import com.stripe.exception.CardException;
import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.PaymentMethod;
import com.stripe.model.SetupIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.CustomerUpdateParams;
import com.stripe.param.PaymentMethodAttachParams;
import com.stripe.param.PaymentMethodUpdateParams;
import com.stripe.param.SetupIntentCreateParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Stripe SDK implementation of {@link StripeCardGateway}. Only Stripe ids, brand, last four
 * digits and expiry ever cross this class: the card number is entered in Stripe Elements and
 * never reaches the backend.
 */
@Slf4j
@Component
public class StripeCardGatewayImp implements StripeCardGateway {

    private static final String CARD_PAYMENT_METHOD_TYPE = "card";
    private static final String USER_UUID_METADATA = "userUuid";
    private static final String CUSTOMER_IDEMPOTENCY_PREFIX = "customer-";

    @Override
    public String createCustomer(final UUID userUuid, final String email, final String name) {
        final CustomerCreateParams params = CustomerCreateParams.builder()
                .setEmail(email)
                .setName(name)
                .putMetadata(USER_UUID_METADATA, userUuid.toString())
                .build();
        // Same key for the same user: two concurrent "add a card" clicks get the same Customer.
        final RequestOptions options = RequestOptions.builder()
                .setIdempotencyKey(CUSTOMER_IDEMPOTENCY_PREFIX + userUuid)
                .build();
        return call("create customer", () -> Customer.create(params, options).getId());
    }

    @Override
    public StripeSetupIntentDto createSetupIntent(final String customerId) {
        final SetupIntentCreateParams params = SetupIntentCreateParams.builder()
                .setCustomer(customerId)
                .setUsage(SetupIntentCreateParams.Usage.OFF_SESSION)
                .addPaymentMethodType(CARD_PAYMENT_METHOD_TYPE)
                .build();
        return call("create setup intent", () -> toDto(SetupIntent.create(params)));
    }

    @Override
    public StripeSetupIntentDto retrieveSetupIntent(final String setupIntentId) {
        return call("retrieve setup intent", () -> toDto(SetupIntent.retrieve(setupIntentId)));
    }

    @Override
    public StripeCardDetailsDto retrievePaymentMethod(final String paymentMethodId) {
        return call("retrieve payment method", () -> toDto(PaymentMethod.retrieve(paymentMethodId)));
    }

    @Override
    public void attachPaymentMethod(final String paymentMethodId, final String customerId) {
        call("attach payment method", () -> PaymentMethod.retrieve(paymentMethodId)
                .attach(PaymentMethodAttachParams.builder().setCustomer(customerId).build()));
    }

    @Override
    public void setDefaultPaymentMethod(final String customerId, final String paymentMethodId) {
        final CustomerUpdateParams params = CustomerUpdateParams.builder()
                .setInvoiceSettings(CustomerUpdateParams.InvoiceSettings.builder()
                        .setDefaultPaymentMethod(paymentMethodId)
                        .build())
                .build();
        call("set default payment method", () -> Customer.retrieve(customerId).update(params));
    }

    @Override
    public void updatePaymentMethod(final String paymentMethodId, final String holderName,
                                    final int expMonth, final int expYear) {
        final PaymentMethodUpdateParams params = PaymentMethodUpdateParams.builder()
                .setBillingDetails(PaymentMethodUpdateParams.BillingDetails.builder().setName(holderName).build())
                .setCard(PaymentMethodUpdateParams.Card.builder()
                        .setExpMonth((long) expMonth)
                        .setExpYear((long) expYear)
                        .build())
                .build();
        call("update payment method", () -> PaymentMethod.retrieve(paymentMethodId).update(params));
    }

    @Override
    public void detachPaymentMethod(final String paymentMethodId) {
        call("detach payment method", () -> PaymentMethod.retrieve(paymentMethodId).detach());
    }

    private static StripeSetupIntentDto toDto(final SetupIntent intent) {
        return new StripeSetupIntentDto(intent.getId(), intent.getClientSecret(), intent.getCustomer(),
                intent.getStatus(), intent.getPaymentMethod());
    }

    private static StripeCardDetailsDto toDto(final PaymentMethod paymentMethod) {
        final PaymentMethod.Card card = paymentMethod.getCard();
        if (card == null) {
            throw new InvalidCardSetupException("Payment method " + paymentMethod.getId() + " is not a card");
        }
        final String holderName = Optional.ofNullable(paymentMethod.getBillingDetails())
                .map(PaymentMethod.BillingDetails::getName)
                .orElse(null);
        return new StripeCardDetailsDto(paymentMethod.getId(), paymentMethod.getCustomer(), card.getBrand(),
                card.getLast4(), Math.toIntExact(card.getExpMonth()), Math.toIntExact(card.getExpYear()), holderName);
    }

    private static <T> T call(final String operation, final StripeCall<T> stripeCall) {
        try {
            return stripeCall.execute();
        } catch (InvalidRequestException | CardException exception) {
            // Unknown id, object of another account, or a card change Stripe refuses: the caller's input.
            log.warn("Stripe rejected {} | code={} | declineCode={}", operation, exception.getCode(),
                    exception instanceof CardException cardException ? cardException.getDeclineCode() : null);
            throw new InvalidCardSetupException("Stripe rejected " + operation + ": " + exception.getCode(), exception);
        } catch (StripeException exception) {
            log.error("Stripe error on {} | code={} | message={}", operation, exception.getCode(), exception.getMessage());
            throw new PaymentProcessingException("Stripe " + operation + " failed", exception);
        }
    }

    @FunctionalInterface
    private interface StripeCall<T> {
        T execute() throws StripeException;
    }
}
