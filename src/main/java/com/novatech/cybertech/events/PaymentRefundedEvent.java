package com.novatech.cybertech.events;

import com.novatech.cybertech.dto.request.stripe.StripeWebhookEventDto;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class PaymentRefundedEvent extends ApplicationEvent {
    private final StripeWebhookEventDto stripeEvent;

    public PaymentRefundedEvent(StripeWebhookEventDto stripeEvent) {
        super(stripeEvent);
        this.stripeEvent = stripeEvent;
    }
}

