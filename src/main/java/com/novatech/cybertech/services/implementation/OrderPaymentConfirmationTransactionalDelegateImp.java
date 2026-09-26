package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.entities.OrderEntity;
import com.novatech.cybertech.entities.enums.OrderStatus;
import com.novatech.cybertech.entities.enums.PaymentAttemptStatus;
import com.novatech.cybertech.entities.enums.TransactionType;
import com.novatech.cybertech.events.PaymentFailedEvent;
import com.novatech.cybertech.events.PaymentRefundedEvent;
import com.novatech.cybertech.events.PaymentSucceededEvent;
import com.novatech.cybertech.exceptions.PaymentNotFoundException;
import com.novatech.cybertech.repositories.OrderRepository;
import com.novatech.cybertech.services.core.CartService;
import com.novatech.cybertech.services.core.OrderPaymentConfirmationTransactionalDelegate;
import com.novatech.cybertech.services.core.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Transactional inner half of the payment-confirmation design. See
 * {@link OrderPaymentConfirmationTransactionalDelegate} for the full "why a separate bean /
 * why this write order" rationale.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderPaymentConfirmationTransactionalDelegateImp implements OrderPaymentConfirmationTransactionalDelegate {

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
     * @throws PaymentNotFoundException when the {@code order_uuid} metadata is missing
     *                                  or no matching order exists
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handlePaymentSuccessWithinTransaction(final PaymentSucceededEvent event) {

        final UUID orderUuid = extractOrderUuid(event.getStripeEvent().getData().getPaymentIntentPayload().getMetadata().get(ORDER_UUID_METADATA_KEY));
        final OrderEntity order = orderRepository.findByUuid(orderUuid).orElseThrow(() -> new PaymentNotFoundException("Order not found for uuid : " + orderUuid));

        if (!PAYMENT_PENDING_STATES.contains(order.getStatus())) {
            log.info("Order {} is in {} (no longer awaiting payment) — ignoring stale/duplicate payment-succeeded event",
                    order.getUuid(), order.getStatus());
            return;
        }

        order.setStatus(OrderStatus.PAID);
        orderRepository.save(order);

        cartService.clearCart(order.getUserEntity().getKeycloakId());

        // Stock/Redis-touching call LAST — see the interface javadoc for why.
        stockService.commitStock(order.getUuid());

        log.info("Order {} marked as PAID", order.getUuid());
    }

    /**
     * Releases the stock reservation and marks the order as
     * {@link OrderStatus#PAYMENT_FAILED} on a Stripe failure.
     *
     * @throws PaymentNotFoundException when {@code order_uuid} metadata is missing or the
     *                                  order is not found
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handlePaymentFailedWithinTransaction(final PaymentFailedEvent event) {
        final UUID orderUuid = extractOrderUuid(event.getStripeEvent().getData().getPaymentIntentPayload().getMetadata().get(ORDER_UUID_METADATA_KEY));
        final OrderEntity order = orderRepository.findByUuid(orderUuid).orElseThrow(() -> new PaymentNotFoundException("Order not found for uuid : " + orderUuid));

        if (!PAYMENT_PENDING_STATES.contains(order.getStatus())) {
            log.info("Order {} is in {} (no longer awaiting payment) — ignoring stale/duplicate payment-failed event",
                    order.getUuid(), order.getStatus());
            return;
        }

        order.setStatus(OrderStatus.PAYMENT_FAILED);
        orderRepository.save(order);

        // Stock/Redis-touching call LAST — see the interface javadoc for why.
        stockService.releaseStock(order.getUuid());

        log.info("Order {} marked as PAYMENT_FAILED", order.getUuid());
    }

    /**
     * Releases the stock reservation and marks the order as {@link OrderStatus#REFUNDED} on a
     * Stripe refund event.
     *
     * @throws PaymentNotFoundException when {@code order_uuid} metadata is missing or the
     *                                  order is not found
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleRefundWithinTransaction(final PaymentRefundedEvent event) {
        final UUID orderUuid = extractOrderUuid(event.getStripeEvent().getData().getPaymentIntentPayload().getMetadata().get(ORDER_UUID_METADATA_KEY));
        final OrderEntity order = orderRepository.findByUuid(orderUuid).orElseThrow(() -> new PaymentNotFoundException("Order not found for uuid : " + orderUuid));

        if (!REFUNDABLE_STATES.contains(order.getStatus())) {
            log.info("Order {} is in {} (not refundable / already refunded) — ignoring stale/duplicate refund event",
                    order.getUuid(), order.getStatus());
            return;
        }

        // Net amount still paid/kept on this order (payments minus refunds already recorded).
        // Mirrors OrderManagementServiceImp#updateOrder's paidAmount calculation so a PARTIAL
        // refund (e.g. the customer dropped one item from an already-paid order) does not get
        // treated as a full refund: releasing stock still reserved for the kept items or flipping
        // the order to the terminal REFUNDED status would be wrong.
        final BigDecimal netPaid = order.getPaymentAttempts().stream()
                .filter(p -> p.getStatus() == PaymentAttemptStatus.SUCCESS)
                .map(p -> p.getTransactionType() == TransactionType.REFUND
                        ? p.getAmount().getAmount().negate()
                        : p.getAmount().getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (netPaid.compareTo(BigDecimal.ZERO) > 0) {
            log.info("Order {} received a partial refund; {} still paid/kept — leaving status at {} and stock untouched.",
                    order.getUuid(), netPaid, order.getStatus());
            return;
        }

        order.setStatus(OrderStatus.REFUNDED);
        orderRepository.save(order);

        // Stock/Redis-touching call LAST — see the interface javadoc for why.
        stockService.releaseStock(order.getUuid());

        log.info("Order {} marked as REFUNDED", order.getUuid());
    }

    /**
     * Null-guard: extracts the order UUID from the raw metadata value, raising a
     * domain-level {@link PaymentNotFoundException} when it is missing or unparseable instead
     * of letting a raw {@link NullPointerException} from {@code UUID.fromString(null)} bubble
     * out to the caller as a fatal.
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
