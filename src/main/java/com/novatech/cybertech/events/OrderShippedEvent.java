package com.novatech.cybertech.events;

import com.novatech.cybertech.dto.data.OrderEventDto;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Published in the transaction that commits the transition to SHIPPED.
 */
@Getter
public class OrderShippedEvent extends ApplicationEvent {

    private final OrderEventDto orderEventDto;

    public OrderShippedEvent(final OrderEventDto orderEventDto) {
        super(orderEventDto);
        this.orderEventDto = orderEventDto;
    }
}
