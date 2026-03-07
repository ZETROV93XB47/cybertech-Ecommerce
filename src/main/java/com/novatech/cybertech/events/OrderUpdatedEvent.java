package com.novatech.cybertech.events;

import com.novatech.cybertech.dto.data.OrderEventDto;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class OrderUpdatedEvent extends ApplicationEvent {

    private final OrderEventDto orderEventDto;

    public OrderUpdatedEvent(Object source, OrderEventDto orderEventDto) {
        super(source);
        this.orderEventDto = orderEventDto;
    }
}
