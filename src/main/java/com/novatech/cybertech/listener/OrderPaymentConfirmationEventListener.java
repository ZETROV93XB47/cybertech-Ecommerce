package com.novatech.cybertech.listener;

import com.novatech.cybertech.entities.OrderEntity;
import com.novatech.cybertech.entities.enums.OrderStatus;
import com.novatech.cybertech.events.PaymentFailedEvent;
import com.novatech.cybertech.events.PaymentRefundedEvent;
import com.novatech.cybertech.events.PaymentSucceededEvent;
import com.novatech.cybertech.exceptions.PaymentNotFoundException;
import com.novatech.cybertech.repositories.OrderRepository;
import com.novatech.cybertech.services.core.CartService;
import com.novatech.cybertech.services.core.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.novatech.cybertech.constants.CyberTechAppConstants.APPLICATION_ASYNC_TASK_EXECUTOR;

/**
 * Bridges Stripe payment lifecycle events into the internal order/stock domain.
 *
 * <p>Each handler reads {@code order_uuid} from the Stripe payment-intent metadata, locates the
 * matching {@link OrderEntity}, performs the appropriate stock side-effect
 * ({@link StockService#commitStock(UUID)} on success;
 * {@link StockService#releaseStock(UUID)} on failure or refund) and flips the
 * {@link OrderStatus} to {@link OrderStatus#PAID}, {@link OrderStatus#PAYMENT_FAILED}
 * or {@link OrderStatus#REFUNDED} respectively.
 *
 * <p>WHY {@link TransactionalEventListener} with {@link TransactionPhase#AFTER_COMMIT}: the
 * Stripe webhook's outer transaction must commit (so the {@code PaymentEntity} row exists)
 * before we touch order status / stock; otherwise a rollback in the producer would leave us
 * having shipped, refunded, or released stock for a payment that never persisted.
 *
 * <p>BUG-124: missing {@code order_uuid} metadata used to NPE through
 * {@code UUID.fromString(null)}; we now extract the value via {@link Optional} and throw a
 * domain-level {@link PaymentNotFoundException} so the listener container surfaces a clean,
 * non-fatal failure.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPaymentConfirmationEventListener {

    private static final String ORDER_UUID_METADATA_KEY = "order_uuid";

    /**
     * Source states from which a payment outcome (success / failure) may still be applied:
     * the order is still awaiting its payment result. Any other state means the order has
     * already advanced (PAID and beyond) or is terminal (CANCELED, REFUNDED), so a
     * late/duplicate Stripe webhook for that order is stale and MUST be ignored — otherwise
     * a delayed {@code payment_intent.succeeded} could resurrect a CANCELED/REFUNDED order
     * to PAID, or a delayed {@code payment_intent.payment_failed} could regress a shipped order.
     * Mirrors {@code OrderManagementServiceImp#isOrderInRetryablePaymentStatus} so a retry
     * (order left in PAYMENT_FAILED) still completes to PAID.
     */
    private static final Set<OrderStatus> PAYMENT_PENDING_STATES =
            EnumSet.of(OrderStatus.CREATED, OrderStatus.AWAITING_PAYMENT, OrderStatus.PAYMENT_FAILED);

    /**
     * States from which a refund may legitimately be applied: the payment has succeeded
     * (PAID and beyond) or the order was canceled and is awaiting its {@code charge.refunded}
     * webhook. Excludes an already-REFUNDED order (idempotent no-op) and the never-paid
     * payment-pending states (nothing to refund).
     */
    private static final Set<OrderStatus> REFUNDABLE_STATES =
            EnumSet.of(OrderStatus.PAID, OrderStatus.AWAITING_SHIPPING, OrderStatus.SHIPPED,
                    OrderStatus.DELIVERED, OrderStatus.RETURNED, OrderStatus.CANCELED);

    private final CartService cartService;
    private final StockService stockService;
    private final OrderRepository orderRepository;

    /**
     * Commits the reserved stock, marks the order as {@link OrderStatus#PAID} and clears the
     * user's cart on a successful Stripe payment.
     *
     * @param event the Stripe-derived {@link PaymentSucceededEvent}
     * @throws PaymentNotFoundException when the {@code order_uuid} metadata is missing
     *                                  (BUG-124 guard) or no matching order exists
     */
    @Async(APPLICATION_ASYNC_TASK_EXECUTOR)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePaymentSuccess(final PaymentSucceededEvent event) {

        final UUID orderUuid = extractOrderUuid(event.getStripeEvent().getData().getPaymentIntentPayload().getMetadata().get(ORDER_UUID_METADATA_KEY));
        final OrderEntity order = orderRepository.findByUuid(orderUuid).orElseThrow(() -> new PaymentNotFoundException("Order not found for uuid : " + orderUuid));

        if (!PAYMENT_PENDING_STATES.contains(order.getStatus())) {
            log.info("Order {} is in {} (no longer awaiting payment) — ignoring stale/duplicate payment-succeeded event",
                    order.getUuid(), order.getStatus());
            return;
        }

        stockService.commitStock(order.getUuid());

        order.setStatus(OrderStatus.PAID);
        orderRepository.save(order);

        cartService.clearCart(order.getUserEntity().getKeycloakId());

        log.info("Order {} marked as PAID", order.getUuid());
    }

    /**
     * Releases the stock reservation and marks the order as
     * {@link OrderStatus#PAYMENT_FAILED} on a Stripe failure.
     *
     * @param event the Stripe-derived {@link PaymentFailedEvent}
     * @throws PaymentNotFoundException when {@code order_uuid} metadata is missing or the
     *                                  order is not found
     */
    @Async(APPLICATION_ASYNC_TASK_EXECUTOR)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePaymentFailed(PaymentFailedEvent event) {
        final UUID orderUuid = extractOrderUuid(event.getStripeEvent().getData().getPaymentIntentPayload().getMetadata().get(ORDER_UUID_METADATA_KEY));
        final OrderEntity order = orderRepository.findByUuid(orderUuid).orElseThrow(() -> new PaymentNotFoundException("Order not found for uuid : " + orderUuid));

        if (!PAYMENT_PENDING_STATES.contains(order.getStatus())) {
            log.info("Order {} is in {} (no longer awaiting payment) — ignoring stale/duplicate payment-failed event",
                    order.getUuid(), order.getStatus());
            return;
        }

        stockService.releaseStock(order.getUuid());

        order.setStatus(OrderStatus.PAYMENT_FAILED);
        orderRepository.save(order);

        log.info("Order {} marked as PAYMENT_FAILED", order.getUuid());
    }

    /**
     * Releases the stock reservation and marks the order as {@link OrderStatus#REFUNDED} on a
     * Stripe refund event.
     *
     * @param event the Stripe-derived {@link PaymentRefundedEvent}
     * @throws PaymentNotFoundException when {@code order_uuid} metadata is missing or the
     *                                  order is not found
     */
    @Async(APPLICATION_ASYNC_TASK_EXECUTOR)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleRefund(final PaymentRefundedEvent event) {
        final UUID orderUuid = extractOrderUuid(event.getStripeEvent().getData().getPaymentIntentPayload().getMetadata().get(ORDER_UUID_METADATA_KEY));
        final OrderEntity order = orderRepository.findByUuid(orderUuid).orElseThrow(() -> new PaymentNotFoundException("Order not found for uuid : " + orderUuid));

        if (!REFUNDABLE_STATES.contains(order.getStatus())) {
            log.info("Order {} is in {} (not refundable / already refunded) — ignoring stale/duplicate refund event",
                    order.getUuid(), order.getStatus());
            return;
        }

        // Release stock on refund
        stockService.releaseStock(order.getUuid());

        order.setStatus(OrderStatus.REFUNDED);
        orderRepository.save(order);

        log.info("Order {} marked as REFUNDED", order.getUuid());
    }

    /**
     * BUG-124 null-guard: extracts the order UUID from the raw metadata value, raising a
     * domain-level {@link PaymentNotFoundException} when it is missing or unparseable instead
     * of letting a raw {@link NullPointerException} from {@code UUID.fromString(null)} bubble
     * out to the listener container as a fatal.
     *
     * @param rawOrderUuid the raw {@code order_uuid} metadata value, may be {@code null}
     * @return the parsed {@link UUID}
     * @throws PaymentNotFoundException when the value is {@code null} or not a valid UUID
     */
    private static UUID extractOrderUuid(final String rawOrderUuid) {
        return Optional.ofNullable(rawOrderUuid)
                .map(value -> {
                    try {
                        return UUID.fromString(value);
                    } catch (IllegalArgumentException ex) {
                        throw new PaymentNotFoundException("Payment event order_uuid metadata is not a valid UUID: " + value);
                    }
                })
                .orElseThrow(() -> new PaymentNotFoundException("Payment event missing order_uuid metadata"));
    }
}
