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

/**
 * Listens for order lifecycle events ({@link OrderCreatedEvent}, {@link OrderUpdatedEvent}) and
 * dispatches the matching notification through the {@link NotificationDispatcher}.
 *
 * <p>Both handlers run {@link Async} on the application task executor so the notification I/O
 * does not block the transaction commit thread.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventListener {

    private final NotificationDispatcher notificationDispatcher;

    /**
     * Sends an {@link NotificationType#ORDER_CONFIRMATION} notification when an order is
     * created.
     *
     * <p>WHY {@link TransactionalEventListener} with {@link TransactionPhase#AFTER_COMMIT}: the
     * confirmation MUST only be sent once the order's row is durably persisted. A pre-commit
     * fire-and-forget would risk telling the user about an order that ultimately rolled back.
     */
    @Async(APPLICATION_ASYNC_TASK_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCreated(final OrderCreatedEvent event) {

        log.info("In order created event listener");

        final Map<String, Object> orderEventDto = new HashMap<>();
        orderEventDto.put("orderEventDto", event.getOrderEventDto());

        final NotificationContext context = NotificationContext.builder()
                .notificationType(NotificationType.ORDER_CONFIRMATION)
                .user(event.getOrderEventDto().getUserContactDto())
                .data(orderEventDto)
                .build();

        notificationDispatcher.dispatch(context);
    }

    /**
     * Sends an {@link NotificationType#ORDER_UPDATE} notification on order mutation
     * (e.g. quantity changes, status transitions surfaced as updates).
     *
     * <p>WHY {@link TransactionalEventListener} with {@link TransactionPhase#AFTER_COMMIT}:
     * see {@link #onOrderCreated(OrderCreatedEvent)} — same reasoning, the user must not be
     * notified of an update that has not yet committed.
     */
    @Async(APPLICATION_ASYNC_TASK_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderUpdated(final OrderUpdatedEvent event) {

        log.info("In order updated event listener");

        final Map<String, Object> orderEventDto = new HashMap<>();
        orderEventDto.put("orderEventDto", event.getOrderEventDto());

        final NotificationContext context = NotificationContext.builder()
                .notificationType(NotificationType.ORDER_UPDATE)
                .user(event.getOrderEventDto().getUserContactDto())
                .data(orderEventDto)
                .build();

        notificationDispatcher.dispatch(context);
    }
}
