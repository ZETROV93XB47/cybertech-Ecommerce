package com.novatech.cybertech.events;

import com.novatech.cybertech.dto.request.stripe.StripeWebhookEventDto;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class PaymentSucceededEvent extends ApplicationEvent {
    private final StripeWebhookEventDto stripeEvent;

    public PaymentSucceededEvent(StripeWebhookEventDto stripeEvent) {
        super(stripeEvent);
        this.stripeEvent = stripeEvent;
    }
}
