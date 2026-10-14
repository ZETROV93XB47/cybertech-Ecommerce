package com.novatech.cybertech.listener;

import com.novatech.cybertech.events.StripePaymentMethodReleasedEvent;
import com.novatech.cybertech.exceptions.PaymentProcessingException;
import com.novatech.cybertech.services.core.StripeCardGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StripePaymentMethodListenerTest {

    @Mock
    private StripeCardGateway stripeCardGateway;

    @InjectMocks
    private StripePaymentMethodListener listener;

    @Test
    void detachesTheReleasedPaymentMethod() {
        listener.onPaymentMethodReleased(new StripePaymentMethodReleasedEvent("pm_old"));

        verify(stripeCardGateway).detachPaymentMethod("pm_old");
    }

    @Test
    void stripeFailureIsLoggedNotPropagated() {
        doThrow(new PaymentProcessingException("Stripe detach payment method failed"))
                .when(stripeCardGateway).detachPaymentMethod("pm_old");

        assertThatCode(() -> listener.onPaymentMethodReleased(new StripePaymentMethodReleasedEvent("pm_old")))
                .doesNotThrowAnyException();
    }

    @Test
    void runsOnlyAfterCommit() throws NoSuchMethodException {
        final var method = StripePaymentMethodListener.class
                .getMethod("onPaymentMethodReleased", StripePaymentMethodReleasedEvent.class);
        assertThat(method.getAnnotation(TransactionalEventListener.class).phase())
                .isEqualTo(TransactionPhase.AFTER_COMMIT);
    }
}
