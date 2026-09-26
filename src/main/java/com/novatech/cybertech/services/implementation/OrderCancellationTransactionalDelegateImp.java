package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.dto.response.order.OrderResponseDto;
import com.novatech.cybertech.entities.OrderEntity;
import com.novatech.cybertech.entities.PaymentEntity;
import com.novatech.cybertech.entities.enums.OrderStatus;
import com.novatech.cybertech.entities.enums.PaymentAttemptStatus;
import com.novatech.cybertech.entities.enums.TransactionType;
import com.novatech.cybertech.entities.valueObjects.Money;
import com.novatech.cybertech.exceptions.CannotCancelOrderException;
import com.novatech.cybertech.exceptions.OrderDoesntBelongsToUserException;
import com.novatech.cybertech.exceptions.OrderNotFoundException;
import com.novatech.cybertech.exceptions.OrderRefundFailedException;
import com.novatech.cybertech.mappers.entity.OrderMapper;
import com.novatech.cybertech.repositories.OrderRepository;
import com.novatech.cybertech.services.core.OrderCancellationTransactionalDelegate;
import com.novatech.cybertech.services.core.PaymentService;
import com.novatech.cybertech.services.core.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderCancellationTransactionalDelegateImp implements OrderCancellationTransactionalDelegate {

    /**
     * Statuses meaning the order has physically entered the fulfillment pipeline — cancellation
     * is blocked from here on, the customer needs the return process instead. Deliberately an
     * explicit set rather than a {@code status.getCode() >= AWAITING_SHIPPING.getCode()} numeric
     * comparison: that comparison used to also match {@code CANCELED}/{@code REFUNDED} (coded
     * higher than AWAITING_SHIPPING for unrelated reasons), which broke idempotent double-cancel
     * — see {@link #isCancellationLockedDueToShipping} javadoc.
     */
    private static final Set<OrderStatus> SHIPPING_IN_PROGRESS_STATUSES = EnumSet.of(
            OrderStatus.AWAITING_SHIPPING, OrderStatus.SHIPPED, OrderStatus.DELIVERED, OrderStatus.RETURNED);

    /** Terminal statuses for which cancelling again is a no-op, not an error. */
    private static final Set<OrderStatus> ALREADY_TERMINAL_STATUSES = EnumSet.of(
            OrderStatus.CANCELED, OrderStatus.REFUNDED);

    private final OrderMapper orderMapper;
    private final OrderRepository orderRepository;
    private final PaymentService paymentService;
    private final StockService stockService;

    @Override
    @Transactional
    public OrderResponseDto cancelWithinTransaction(final UUID orderUUID, final Jwt jwt) {
        final OrderEntity orderEntity = orderRepository.findByUuid(orderUUID)
                .orElseThrow(() -> new OrderNotFoundException("Order with UUID " + orderUUID + " not found"));

        final String keycloakId = OrderManagementServiceImp.resolveKeycloakIdFromJwt(jwt);
        if (!OrderManagementServiceImp.isCurrentUserOrderInitiator(orderEntity, keycloakId)) {
            log.info("User tried to cancel and order not linked to his account");
            throw new OrderDoesntBelongsToUserException("Order with UUID " + orderUUID + " not found for this user account");
        }

        // Idempotency: checked BEFORE the shipping lock below. A previous cancel attempt (or a
        // concurrent one) may have already moved the order to CANCELED/REFUNDED, and those
        // terminal states are not "shipped" — they must return the current snapshot, not be
        // mistaken for an in-flight shipment and rejected with a confusing error.
        if (ALREADY_TERMINAL_STATUSES.contains(orderEntity.getStatus())) {
            log.info("Order {} already {} — returning current state.", orderUUID, orderEntity.getStatus());
            return orderMapper.mapFromEntityToResponseDto(orderEntity);
        }

        if (isCancellationLockedDueToShipping(orderEntity)) {
            log.info("User tried to cancel an order whose status is at or beyond AWAITING_SHIPPING");
            throw new CannotCancelOrderException("Order is already shipped and can't be cancelled, please consider initiating Return process");
        }

        // Attempt every refund BEFORE touching order status or stock. paymentService.refund(...)
        // can come back with a non-exceptional FAILED/CANCELED result (e.g. Stripe rejects the
        // refund — closed card, insufficient merchant balance) without throwing. Ignoring that
        // result used to let the order become CANCELED (and stock get released) even though the
        // customer was never actually refunded. .toList() forces every refund to be attempted
        // (no short-circuit) before we decide whether to proceed.
        //
        // Each payment refunds only its OWN remaining balance (its amount minus whatever has
        // already been refunded against it specifically — e.g. a prior updateOrder partial
        // refund), not its full original amount: Stripe rejects a refund request that exceeds
        // what is still refundable on a charge, which used to make cancelOrder fail outright for
        // any order that had already been partially refunded once. A payment with nothing left to
        // refund is skipped rather than sent to Stripe as a pointless zero/negative request.
        final boolean anyRefundFailed = orderEntity.getPaymentAttempts().stream()
                .filter(p -> p.getStatus() == PaymentAttemptStatus.SUCCESS)
                .filter(p -> p.getTransactionType() == TransactionType.PAYMENT)
                .map(p -> Map.entry(p, remainingRefundable(orderEntity, p)))
                .filter(entry -> entry.getValue().compareTo(BigDecimal.ZERO) > 0)
                .map(entry -> paymentService.refund(
                        orderEntity,
                        entry.getKey().getPaymentType(),
                        new Money(entry.getValue(), entry.getKey().getAmount().getCurrencyCode()),
                        entry.getKey().getIdempotencyKey()))
                .toList()
                .stream()
                .anyMatch(refund -> refund.getStatus() != PaymentAttemptStatus.SUCCESS);

        if (anyRefundFailed) {
            throw new OrderRefundFailedException(
                    "Refund was rejected while cancelling order " + orderUUID + " — cancellation aborted, order left unchanged");
        }

        orderEntity.setStatus(OrderStatus.CANCELED);

        // Save BEFORE releasing stock — releaseStock ends with a Redis write that is NOT part of
        // this transaction. If save() throws (e.g. OptimisticLockingFailureException racing the
        // async OrderPaymentConfirmationEventListener — @Retryable on cancelOrder absorbs that),
        // nothing has touched Redis yet and the whole transaction rolls back cleanly. Reversing
        // this order would leave a deleted Redis TTL sentinel behind for a reservation the DB
        // rollback just restored to ACTIVE/COMMITTED.
        final OrderEntity saved = orderRepository.save(orderEntity);

        // Release any reserved stock for the cancelled order — mirrors deleteByUUID(). Handles
        // both an ACTIVE reservation (never committed) and a COMMITTED one (order already PAID):
        // StockService restores product.stock in the latter case so cancelling a paid order
        // never leaks inventory.
        stockService.releaseStock(orderUUID);

        return orderMapper.mapFromEntityToResponseDto(saved);
    }

    /**
     * How much of {@code payment} is still refundable: its original amount minus every
     * {@code SUCCESS} refund already linked to it via {@link PaymentEntity#getOriginalPayment()}.
     * A payment that was already fully refunded (e.g. by an earlier {@code updateOrder} partial
     * refund covering the whole amount) returns zero, not a negative number — floor is not needed
     * beyond the caller's {@code > 0} filter, but the subtraction itself never goes below what was
     * actually charged.
     */
    private static BigDecimal remainingRefundable(final OrderEntity order, final PaymentEntity payment) {
        final BigDecimal alreadyRefunded = order.getPaymentAttempts().stream()
                .filter(r -> r.getTransactionType() == TransactionType.REFUND)
                .filter(r -> r.getStatus() == PaymentAttemptStatus.SUCCESS)
                .filter(r -> r.getOriginalPayment() != null
                        && java.util.Objects.equals(payment.getId(), r.getOriginalPayment().getId()))
                .map(r -> r.getAmount().getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return payment.getAmount().getAmount().subtract(alreadyRefunded);
    }

    /**
     * {@code true} when the order's status is in {@link #SHIPPING_IN_PROGRESS_STATUSES} — the
     * order has physically entered fulfillment.
     *
     * <p>By the time an order reaches {@code AWAITING_SHIPPING}, the async
     * {@code OrderPaymentConfirmationEventListener} has already called
     * {@code stockService.commitStock()} for it — but {@code releaseStock} correctly reverses a
     * committed reservation (restores {@code productEntity.stock}), so cancelling a merely-{@code
     * PAID} order is safe and does restore inventory. This guard exists purely for the business
     * rule that once fulfillment has physically started, the customer must use the Return process
     * instead of a plain cancel — not to work around a stock-restoration limitation.</p>
     *
     * <p>Callers must check {@link #ALREADY_TERMINAL_STATUSES} first — {@code CANCELED} and
     * {@code REFUNDED} are unrelated to shipping and must never reach this check.</p>
     */
    private static boolean isCancellationLockedDueToShipping(final OrderEntity orderEntity) {
        return SHIPPING_IN_PROGRESS_STATUSES.contains(orderEntity.getStatus());
    }
}
