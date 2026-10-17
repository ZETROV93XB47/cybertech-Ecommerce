package com.novatech.cybertech.events;

import com.novatech.cybertech.dto.request.stripe.StripeWebhookEventDto;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class PaymentFailedEvent extends ApplicationEvent {
    private final StripeWebhookEventDto stripeEvent;

    public PaymentFailedEvent(Object source, StripeWebhookEventDto stripeEvent) {
        super(source);
        this.stripeEvent = stripeEvent;
    }

    public PaymentFailedEvent(StripeWebhookEventDto stripeEvent) {
        super(stripeEvent);
        this.stripeEvent = stripeEvent;
    }
}
