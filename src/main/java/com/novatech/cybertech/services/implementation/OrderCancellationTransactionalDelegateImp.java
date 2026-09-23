package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.dto.response.order.OrderResponseDto;
import com.novatech.cybertech.entities.OrderEntity;
import com.novatech.cybertech.entities.enums.OrderStatus;
import com.novatech.cybertech.entities.enums.PaymentAttemptStatus;
import com.novatech.cybertech.entities.enums.TransactionType;
import com.novatech.cybertech.exceptions.CannotCancelOrderException;
import com.novatech.cybertech.exceptions.OrderDoesntBelongsToUserException;
import com.novatech.cybertech.exceptions.OrderNotFoundException;
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

import java.util.EnumSet;
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

        orderEntity.setStatus(OrderStatus.CANCELED);

        orderEntity.getPaymentAttempts().stream()
                .filter(p -> p.getStatus() == PaymentAttemptStatus.SUCCESS)
                .filter(p -> p.getTransactionType() == TransactionType.PAYMENT)
                .forEach(paymentAttemptEntity -> paymentService.refund(orderEntity, paymentAttemptEntity.getPaymentType(), paymentAttemptEntity.getAmount(), paymentAttemptEntity.getIdempotencyKey()));

        // Release any reserved stock for the cancelled order — mirrors deleteByUUID().
        // Idempotent: a no-op if the async PAID listener already committed the reservation.
        stockService.releaseStock(orderUUID);

        return orderMapper.mapFromEntityToResponseDto(orderRepository.save(orderEntity));
    }

    /**
     * {@code true} when the order's status is in {@link #SHIPPING_IN_PROGRESS_STATUSES} — the
     * order has physically entered fulfillment.
     *
     * <p>Cancellation lock is stricter than the {@code updateOrder} shipping guard: by the time
     * an order reaches {@code AWAITING_SHIPPING}, the async {@code OrderPaymentConfirmationEventListener}
     * has already called {@code stockService.commitStock()} which decrements
     * {@code productEntity.stock} and removes the reservation. A subsequent cancel would refund the
     * customer but {@code stockService.releaseStock(orderUUID)} is then a no-op — the stock would
     * never come back. Block cancel here so the merchant can keep the inventory until shipping
     * actually leaves the warehouse, or until a return process is initiated post-delivery.</p>
     *
     * <p>Callers must check {@link #ALREADY_TERMINAL_STATUSES} first — {@code CANCELED} and
     * {@code REFUNDED} are unrelated to shipping and must never reach this check.</p>
     */
    private static boolean isCancellationLockedDueToShipping(final OrderEntity orderEntity) {
        return SHIPPING_IN_PROGRESS_STATUSES.contains(orderEntity.getStatus());
    }
}
