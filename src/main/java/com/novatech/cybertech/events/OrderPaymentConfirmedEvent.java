package com.novatech.cybertech.events;

import com.novatech.cybertech.dto.data.OrderEventDto;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Published once an order has actually moved to {@code PAID} on a {@code payment_intent.succeeded}
 * webhook — i.e. after the transition is applied, not on every (possibly duplicate) webhook.
 * Consumed AFTER_COMMIT to send the payment-confirmation notification.
 */
@Getter
public class OrderPaymentConfirmedEvent extends ApplicationEvent {

    private final OrderEventDto orderEventDto;

    public OrderPaymentConfirmedEvent(final OrderEventDto orderEventDto) {
        super(orderEventDto);
        this.orderEventDto = orderEventDto;
    }
}
