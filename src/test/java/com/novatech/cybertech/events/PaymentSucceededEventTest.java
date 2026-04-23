package com.novatech.cybertech.events;

import com.novatech.cybertech.dto.request.stripe.StripeWebhookEventDto;
import com.novatech.cybertech.fixtures.dto.PaymentDtoFixtures;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PaymentSucceededEvent}. Single-arg ctor uses payload as Spring source.
 */
class PaymentSucceededEventTest {

    @Test
    void singleArgConstructorShouldUsePayloadAsSourceAndExposeIt() {
        StripeWebhookEventDto stripe = PaymentDtoFixtures.aValidPaymentSucceededEvent();

        PaymentSucceededEvent event = new PaymentSucceededEvent(stripe);

        assertThat(event.getSource()).isSameAs(stripe);
        assertThat(event.getStripeEvent()).isSameAs(stripe);
    }
}
