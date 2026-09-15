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

import java.util.UUID;

import static com.novatech.cybertech.entities.enums.OrderStatus.AWAITING_SHIPPING;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderCancellationTransactionalDelegateImp implements OrderCancellationTransactionalDelegate {

    private final OrderMapper orderMapper;
    private final OrderRepository orderRepository;
    private final PaymentService paymentService;
    private final StockService stockService;

    @Override
    @Transactional
    public OrderResponseDto cancelWithinTransaction(final UUID orderUUID, final Jwt jwt) {
        final OrderEntity orderEntity = orderRepository.findByUuid(orderUUID)
                .orElseThrow(() -> new OrderNotFoundException("Order with UUID " + orderUUID + " not found"));

        if (isCancellationLockedDueToShipping(orderEntity)) {
            log.info("User tried to cancel an order whose status is at or beyond AWAITING_SHIPPING");
            throw new CannotCancelOrderException("Order is already shipped and can't be cancelled, please consider initiating Return process");
        }

        final String keycloakId = OrderManagementServiceImp.resolveKeycloakIdFromJwt(jwt);
        if (!OrderManagementServiceImp.isCurrentUserOrderInitiator(orderEntity, keycloakId)) {
            log.info("User tried to cancel and order not linked to his account");
            throw new OrderDoesntBelongsToUserException("Order with UUID " + orderUUID + " not found for this user account");
        }

        // Idempotency: if a concurrent caller (or a previous retry attempt) already cancelled the
        // order, return the current snapshot rather than double-refunding.
        if (orderEntity.getStatus() == OrderStatus.CANCELED) {
            log.info("Order {} already CANCELED — returning current state.", orderUUID);
            return orderMapper.mapFromEntityToResponseDto(orderEntity);
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
     * {@code true} when the order's status is at or beyond {@link OrderStatus#AWAITING_SHIPPING}.
     *
     * <p>Cancellation lock is stricter than the {@code updateOrder} shipping guard: by the time
     * an order reaches {@code AWAITING_SHIPPING}, the async {@code OrderPaymentConfirmationEventListener}
     * has already called {@code stockService.commitStock()} which decrements
     * {@code productEntity.stock} and removes the reservation. A subsequent cancel would refund the
     * customer but {@code stockService.releaseStock(orderUUID)} is then a no-op — the stock would
     * never come back. Block cancel here so the merchant can keep the inventory until shipping
     * actually leaves the warehouse, or until a return process is initiated post-delivery.</p>
     */
    private static boolean isCancellationLockedDueToShipping(final OrderEntity orderEntity) {
        return orderEntity.getStatus().getCode() >= AWAITING_SHIPPING.getCode();
    }
}
