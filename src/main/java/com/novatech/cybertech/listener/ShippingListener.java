package com.novatech.cybertech.listener;

import com.novatech.cybertech.dispatcher.ShippingDispatcher;
import com.novatech.cybertech.dto.data.OrderEventDto;
import com.novatech.cybertech.dto.data.ShippingContext;
import com.novatech.cybertech.dto.data.UserContactDto;
import com.novatech.cybertech.entities.BaseEntity;
import com.novatech.cybertech.entities.OrderEntity;
import com.novatech.cybertech.entities.PaymentEntity;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.entities.enums.OrderStatus;
import com.novatech.cybertech.entities.enums.PaymentAttemptStatus;
import com.novatech.cybertech.events.OrderPaidEvent;
import com.novatech.cybertech.events.OrderShippedEvent;
import com.novatech.cybertech.exceptions.OrderNotFoundException;
import com.novatech.cybertech.repositories.OrderRepository;
import com.novatech.cybertech.utils.UserContactUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.Comparator;

import static com.novatech.cybertech.constants.CyberTechAppConstants.APPLICATION_ASYNC_TASK_EXECUTOR;


/**
 * Listens for {@link OrderPaidEvent} and orchestrates the shipping side-effects: dispatch to the
 * configured shipping provider, flip the {@link OrderEntity} status to
 * {@link OrderStatus#SHIPPED}, then republish an {@link OrderShippedEvent} that
 * {@link NotificationListener} consumes to send the shipping-confirmation notification.
 *
 * <p>Notifications are intentionally NOT dispatched from this listener: the
 * {@link OrderShippedEvent} fan-out via {@link NotificationListener} is the single source of
 * truth for the shipping-confirmation channel — an orphan local
 * {@code NotificationContext} that was built here but never dispatched was removed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShippingListener {

    private final OrderRepository orderRepository;
    private final ShippingDispatcher shippingDispatcher;
    private final ApplicationEventPublisher eventPublisher;


    /**
     * Handles {@link OrderPaidEvent} after the publishing transaction commits.
     *
     * <p>WHY {@link TransactionalEventListener} with {@link TransactionPhase#AFTER_COMMIT}: we
     * MUST NOT trigger external shipping or downstream notifications until the order's PAID
     * state is durably persisted. Listening before commit would race the DB write and could
     * dispatch a real shipment for a transaction that ultimately rolled back.
     *
     * <p>WHY {@link Propagation#REQUIRES_NEW}: this listener runs asynchronously and writes the
     * SHIPPED status; it must do so in its own transaction so its outcome cannot interfere with
     * the (already committed) caller's transaction state.
     *
     * <p>Downstream effect: the {@link OrderShippedEvent} published at the end is consumed by
     * {@link NotificationListener#on(OrderShippedEvent)} which sends the user-facing shipping
     * confirmation. This listener does not dispatch a notification directly.
     */
    @Async(APPLICATION_ASYNC_TASK_EXECUTOR)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(final OrderPaidEvent orderPaidEvent) {
        log.info("OrderPaidEvent received");

        final OrderEntity order = orderRepository.findByUuid(orderPaidEvent.getOrderUUID()).orElseThrow(() -> new OrderNotFoundException("Order " + orderPaidEvent.getOrderUUID() + " not found"));

        if (order.getStatus() != OrderStatus.PAID) return;

        // Guards a narrow but real window: updateOrder's charge-difference path can regress an
        // AWAITING_SHIPPING order back to AWAITING_PAYMENT (to fund a top-up), then the payment
        // webhook re-promotes it to PAID and republishes OrderPaidEvent — which would otherwise
        // land here a second time while THIS order's original dispatch is still in flight (or has
        // already completed). shippedAt is the durable idempotency marker for the one thing here
        // that must never repeat: the actual call to the carrier.
        if (order.getShippedAt() != null) {
            log.info("Order {} was already handed to the carrier at {} — ignoring a re-promoted OrderPaidEvent instead of dispatching again.", order.getUuid(), order.getShippedAt());
            return;
        }

        // Atomically claim the order: flip PAID -> AWAITING_SHIPPING and save FIRST so JPA's
        // @Version optimistic locking detects the race against ShipAllPaidOrdersTasklet
        // (which also ships PAID orders). If we lose the race, the OptimisticLockingFailureException
        // bubbles up and we skip dispatching — preventing the double-ship bug. shippedAt is stamped
        // in the SAME save, so it is claimed under the same optimistic-lock guarantee.
        order.setStatus(OrderStatus.AWAITING_SHIPPING);
        order.setShippedAt(LocalDateTime.now());
        orderRepository.save(order);

        final UserEntity user = order.getUserEntity();
        if (user == null) {
            // Structurally possible — userEntity is the only nullable column on orderTable — but
            // never legitimately reachable (an order cannot be placed without a user). Fail loudly
            // instead of a bare NPE so the async exception handler's log line is actionable.
            throw new IllegalStateException("Order " + order.getUuid() + " has no associated user — cannot resolve shipping contact details.");
        }

        final UserContactDto userContactDto = UserContactUtils.toUserContact(user);

        final ShippingContext shippingContext = ShippingContext.builder()
                .user(userContactDto)
                .packageId(order.getUuid().toString())
                .shippingType(order.getShippingType())
                .shippingProvider(order.getShippingProvider())
                .build();

        shippingDispatcher.dispatch(shippingContext);

        log.info("Order shipped successfully");

        order.setStatus(OrderStatus.SHIPPED);

        orderRepository.save(order);

        // An orphan NotificationContext local was built here but never dispatched — the actual
        // shipping-confirmation notification is sent by NotificationListener when it consumes
        // the OrderShippedEvent below. Dead code removed.

        // Guard against empty paymentAttempts (race condition where the listener fires before
        // flush, or migrated orders with no legacy attempts).
        final PaymentAttemptStatus lastAttemptStatus = order.getPaymentAttempts().stream()
                .max(Comparator.comparing(BaseEntity::getCreatedAt))
                .map(PaymentEntity::getStatus)
                .orElse(PaymentAttemptStatus.SUCCESS);

        final OrderEventDto orderEventDto = OrderEventDto.builder()
                .orderUuid(order.getUuid())
                .totalAmount(order.getTotalAmount().getAmount())
                .orderStatus(order.getStatus())
                .userContactDto(userContactDto)
                .shippingType(order.getShippingType())
                .shippingProvider(order.getShippingProvider())
                .paymentAttemptStatus(lastAttemptStatus)
                .build();


        eventPublisher.publishEvent(new OrderShippedEvent(orderEventDto));
        log.info("OrderShippedEvent published successfully");
    }
}
