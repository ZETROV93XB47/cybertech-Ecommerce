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

import static com.novatech.cybertech.constants.CyberTechAppConstants.APPLICATION_ASYNC_TASK_EXECUTOR;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventListener {

    private final NotificationDispatcher notificationDispatcher;

    @Async(APPLICATION_ASYNC_TASK_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCreated(final OrderCreatedEvent event) {

        log.info("In order created event listener");

        Map<String, Object> orderEventDto = new HashMap<>();
        orderEventDto.put("orderEventDto", event.getOrderEventDto());

        final NotificationContext context = NotificationContext.builder()
                .notificationType(NotificationType.ORDER_CONFIRMATION)
                .user(event.getOrderEventDto().getUserContactDto())
                .data(orderEventDto)
                .build();

        notificationDispatcher.dispatch(context);
    }

    @Async(APPLICATION_ASYNC_TASK_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderUpdated(final OrderUpdatedEvent event) {

        log.info("In order updated event listener");

        Map<String, Object> orderEventDto = new HashMap<>();
        orderEventDto.put("orderEventDto", event.getOrderEventDto());

        final NotificationContext context = NotificationContext.builder()
                .notificationType(NotificationType.ORDER_UPDATE)
                .user(event.getOrderEventDto().getUserContactDto())
                .data(orderEventDto)
                .build();

        notificationDispatcher.dispatch(context);
    }
}
