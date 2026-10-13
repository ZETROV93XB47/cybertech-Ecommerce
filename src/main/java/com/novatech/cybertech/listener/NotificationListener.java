package com.novatech.cybertech.listener;

import com.novatech.cybertech.dispatcher.NotificationDispatcher;
import com.novatech.cybertech.dto.data.NotificationContext;
import com.novatech.cybertech.dto.data.OrderConfirmationPayload;
import com.novatech.cybertech.entities.NotificationEntity;
import com.novatech.cybertech.entities.enums.EmailTemplateType;
import com.novatech.cybertech.entities.enums.NotificationSubject;
import com.novatech.cybertech.events.OrderPaymentConfirmedEvent;
import com.novatech.cybertech.services.core.NotificationRetryableDelivery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import static com.novatech.cybertech.constants.CyberTechAppConstants.APPLICATION_ASYNC_TASK_EXECUTOR;
import static com.novatech.cybertech.entities.enums.NotificationType.PAYMENT_CONFIRMATION;

/**
 * Sends the payment-confirmation notification on {@link OrderPaymentConfirmedEvent}, through the
 * {@link NotificationRetryableDelivery} for the user's preferred communication channel, which in
 * turn calls the {@link NotificationDispatcher}. (The shipping confirmation is sent by
 * {@code ShipAllPaidOrdersTasklet}, the only shipping path.)
 *
 * <p><b>Phase 2 hardening:</b> retries are now Resilience4j-driven. The
 * hand-rolled three-attempt {@code for} loop and the SENT/FAILED audit-row
 * branches that lived inline have been collapsed into a single
 * {@link NotificationRetryableDelivery#deliver(NotificationContext)} call. The
 * retry policy lives under the {@code notificationDispatch} instance in
 * {@code application.properties} (see {@code resilience4j.retry.instances.notificationDispatch.*}
 * — the cap is exposed via {@code cybertech.notification.dispatch.max-attempts}
 * for operator-friendly tuning).
 *
 * <p>Retry contract: dispatch is attempted up to
 * {@code cybertech.notification.dispatch.max-attempts} times with exponential
 * backoff on {@link com.novatech.cybertech.exceptions.NotificationDeliveryException}.
 * Programmer-error
 * ({@link com.novatech.cybertech.exceptions.NoStrategyFoundForProcessingTheRequest})
 * is on the {@code ignore-exceptions} allowlist and fails fast. The persisted
 * {@link NotificationEntity} reflects the final outcome — {@code SENT} on a
 * successful attempt, {@code PENDING_RETRY} when the in-process budget is
 * exhausted (Phase 3's batch tasklet then redrives the row; only that tasklet
 * may write terminal {@code FAILED}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationListener {

    private final NotificationRetryableDelivery retryableDelivery;

    /**
     * Sends the payment-confirmation notification once the {@code payment_intent.succeeded} webhook
     * has moved the order to {@code PAID}. The order-confirmation email sent at {@code placeOrder}
     * usually still says "payment pending", since Stripe confirms asynchronously.
     *
     * <p>WHY {@link TransactionalEventListener} with {@link TransactionPhase#AFTER_COMMIT}: the
     * user must not be told the payment is confirmed before the PAID status is durably persisted.
     */
    @Async(APPLICATION_ASYNC_TASK_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(final OrderPaymentConfirmedEvent orderPaymentConfirmedEvent) {
        final var dto = orderPaymentConfirmedEvent.getOrderEventDto();
        log.info("Received OrderPaymentConfirmedEvent for order {}", dto.getOrderUuid());

        final NotificationContext<OrderConfirmationPayload> notificationContext = NotificationContext.<OrderConfirmationPayload>builder()
                .payload(OrderConfirmationPayload.builder()
                        .orderUuid(dto.getOrderUuid())
                        .totalAmount(dto.getTotalAmount())
                        .orderStatus(dto.getOrderStatus())
                        .userContactDto(dto.getUserContactDto())
                        .build())
                .subject(NotificationSubject.PAYMENT_CONFIRMATION.getSubject())
                .user(dto.getUserContactDto())
                .templatePath(EmailTemplateType.PAYMENT_CONFIRMATION.getTemplatePath())
                .notificationType(PAYMENT_CONFIRMATION)
                .communicationChanel(dto.getUserContactDto().getDefaultCommunicationChanel())
                .build();

        retryableDelivery.deliver(notificationContext);
    }
}
