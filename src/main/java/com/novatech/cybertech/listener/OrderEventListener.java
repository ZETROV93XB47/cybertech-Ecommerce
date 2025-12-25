package com.novatech.cybertech.listener;

import com.novatech.cybertech.dispatcher.NotificationDispatcher;
import com.novatech.cybertech.dto.data.NotificationContext;
import com.novatech.cybertech.entities.enums.NotificationType;
import com.novatech.cybertech.events.OrderCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventListener {

    private final NotificationDispatcher notificationDispatcher;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCreated(final OrderCreatedEvent event) {

        final NotificationContext context = NotificationContext.builder()
                .notificationType(NotificationType.ORDER_CONFIRMATION)
                .user(event.getOrderEventDto().getUserContactDto())
                //.data((Map<String, Object>) new HashMap<>().put("OrderEventDto", event.getOrderEventDto()))
                .payload(event.getOrderEventDto())//TODO: Clean this redundant part later
                .message("Votre commande #" + event.getOrderEventDto().getOrderUuid() + " a bien été confirmée.")//!WARNING Potentielle redondance de orderDto
                .build();

        notificationDispatcher.dispatch(context);
    }
}
