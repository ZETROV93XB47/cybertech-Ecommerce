package com.novatech.cybertech.events;

import com.novatech.cybertech.dto.data.OrderEventDto;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class OrderShippedEvent extends ApplicationEvent {

    private final OrderEventDto orderEventDto;

    public OrderShippedEvent(OrderEventDto orderEventDto) {
        super(orderEventDto); // On utilise le NotificationContext comme source par défaut pour satisfaire ApplicationEvent
        this.orderEventDto = orderEventDto;
    }
}
