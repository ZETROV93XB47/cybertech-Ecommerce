package com.novatech.cybertech.listener;

import com.novatech.cybertech.events.StripePaymentMethodReleasedEvent;
import com.novatech.cybertech.services.core.StripeCardGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Detaches a released card from its Stripe Customer once the database no longer references it.
 *
 * <p>Synchronous on purpose (the user waits for "card removed"), but a Stripe failure is only
 * logged: the card is already gone from the account and can no longer be charged by the backend;
 * the leftover PaymentMethod on the Customer is harmless and can be detached from the dashboard.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StripePaymentMethodListener {

    private final StripeCardGateway stripeCardGateway;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentMethodReleased(final StripePaymentMethodReleasedEvent event) {
        try {
            stripeCardGateway.detachPaymentMethod(event.getPaymentMethodId());
            log.info("Stripe payment method {} detached", event.getPaymentMethodId());
        } catch (RuntimeException exception) {
            log.warn("Could not detach Stripe payment method {}: {}", event.getPaymentMethodId(), exception.getMessage());
        }
    }
}
