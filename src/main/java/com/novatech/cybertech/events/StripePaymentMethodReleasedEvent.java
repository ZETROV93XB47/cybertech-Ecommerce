package com.novatech.cybertech.events;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * A saved card was replaced or deleted: its Stripe PaymentMethod must be detached from the
 * Customer. Published inside the transaction that drops the card; the detach runs after commit,
 * so a rolled-back change never leaves the user with a card Stripe can no longer charge.
 */
@Getter
public class StripePaymentMethodReleasedEvent extends ApplicationEvent {

    private final String paymentMethodId;

    public StripePaymentMethodReleasedEvent(final String paymentMethodId) {
        super(paymentMethodId);
        this.paymentMethodId = paymentMethodId;
    }
}
