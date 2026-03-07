package com.novatech.cybertech.listener;

import com.novatech.cybertech.dispatcher.NotificationDispatcher;
import com.novatech.cybertech.dto.data.NotificationContext;
import com.novatech.cybertech.entities.enums.NotificationType;
import com.novatech.cybertech.events.OrderCreatedEvent;
import com.novatech.cybertech.events.OrderUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventListener {

    private final NotificationDispatcher notificationDispatcher;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCreated(final OrderCreatedEvent event) {

        log.info("In order created event listener");

        Map<String, Object> orderEventDto = new HashMap<>();
        orderEventDto.put("orderEventDto", event.getOrderEventDto());

        final NotificationContext context = NotificationContext.builder()
                .notificationType(NotificationType.ORDER_CONFIRMATION)
                .user(event.getOrderEventDto().getUserContactDto())
                .data(orderEventDto)
                .message("Votre commande #" + event.getOrderEventDto().getOrderUuid() + " a bien été confirmée.")//!WARNING Potentielle redondance de orderDto
                .build();

        notificationDispatcher.dispatch(context);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderUpdated(final OrderUpdatedEvent event) {

        log.info("In order updated event listener");

        Map<String, Object> orderEventDto = new HashMap<>();
        orderEventDto.put("orderEventDto", event.getOrderEventDto());

        final NotificationContext context = NotificationContext.builder()
                .notificationType(NotificationType.ORDER_UPDATE)
                .user(event.getOrderEventDto().getUserContactDto())
                .data(orderEventDto)
                .message("Votre commande #" + event.getOrderEventDto().getOrderUuid() + " a été mise à jour.")
                .build();

        notificationDispatcher.dispatch(context);
    }
}
