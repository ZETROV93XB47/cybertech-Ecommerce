package com.novatech.cybertech.events;

import com.novatech.cybertech.dto.request.stripe.StripeWebhookEventDto;
import com.novatech.cybertech.fixtures.dto.PaymentDtoFixtures;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PaymentFailedEvent}. Both ctors covered.
 */
class PaymentFailedEventTest {

    @Test
    void twoArgConstructorShouldExposeSourceAndPayload() {
        Object source = new Object();
        StripeWebhookEventDto stripe = PaymentDtoFixtures.aValidPaymentSucceededEvent();

        PaymentFailedEvent event = new PaymentFailedEvent(source, stripe);

        assertThat(event.getSource()).isSameAs(source);
        assertThat(event.getStripeEvent()).isSameAs(stripe);
    }

    @Test
    void singleArgConstructorShouldUsePayloadAsSourceAndExposeIt() {
        StripeWebhookEventDto stripe = PaymentDtoFixtures.aValidPaymentSucceededEvent();

        PaymentFailedEvent event = new PaymentFailedEvent(stripe);

        assertThat(event.getSource()).isSameAs(stripe);
        assertThat(event.getStripeEvent()).isSameAs(stripe);
    }
}
