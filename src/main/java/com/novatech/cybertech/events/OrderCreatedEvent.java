package com.novatech.cybertech.events;

import com.novatech.cybertech.dto.data.OrderEventDto;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class OrderCreatedEvent extends ApplicationEvent {

    private final OrderEventDto orderEventDto;

    public OrderCreatedEvent(Object source, OrderEventDto orderEventDto) {
        super(source);
        this.orderEventDto = orderEventDto;
    }
}
