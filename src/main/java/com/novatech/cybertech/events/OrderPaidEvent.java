package com.novatech.cybertech.events;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

@Getter
public class OrderPaidEvent extends ApplicationEvent {
    private final UUID orderUUID;

    public OrderPaidEvent(Object source, UUID orderUUID) {
        super(source);
        this.orderUUID = orderUUID;
    }

    public OrderPaidEvent(UUID orderUUID) {
        super(orderUUID); // On utilise l'UUID comme source par défaut pour satisfaire ApplicationEvent
        this.orderUUID = orderUUID;
    }
}
