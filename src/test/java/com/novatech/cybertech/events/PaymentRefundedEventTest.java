package com.novatech.cybertech.events;

import com.novatech.cybertech.dto.request.stripe.StripeWebhookEventDto;
import com.novatech.cybertech.fixtures.dto.PaymentDtoFixtures;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PaymentRefundedEvent}. Single-arg ctor uses payload as Spring source.
 */
class PaymentRefundedEventTest {

    @Test
    void singleArgConstructorShouldUsePayloadAsSourceAndExposeIt() {
        StripeWebhookEventDto stripe = PaymentDtoFixtures.aValidPaymentSucceededEvent();

        PaymentRefundedEvent event = new PaymentRefundedEvent(stripe);

        assertThat(event.getSource()).isSameAs(stripe);
        assertThat(event.getStripeEvent()).isSameAs(stripe);
    }
}
