package com.novatech.cybertech.batch.task;

import com.novatech.cybertech.dispatcher.ShippingDispatcher;
import com.novatech.cybertech.dto.data.ShippingContext;
import com.novatech.cybertech.entities.OrderEntity;
import com.novatech.cybertech.entities.enums.OrderStatus;
import com.novatech.cybertech.exceptions.OrderNotFoundException;
import com.novatech.cybertech.repositories.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Per-order claim+dispatch helper invoked by {@link ShipAllPaidOrdersTasklet} in its own
 * {@link Propagation#REQUIRES_NEW} transaction.
 *
 * <p>The previous implementation kept everything inside the tasklet's
 * outer {@code @Transactional} method. With a single Hibernate session covering all orders,
 * the optimistic-lock UPDATE only flushed at end-of-method — so the per-order
 * {@code try/catch} on {@link org.springframework.dao.OptimisticLockingFailureException}
 * could never observe a race-loss; the exception escaped the loop and aborted the whole
 * batch step. By moving the claim+dispatch into a {@code REQUIRES_NEW} method the flush
 * happens at this delegate's commit boundary, which is INSIDE the tasklet's
 * {@code try/catch} call site — so race-losses now skip cleanly.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShipOrderTransactionalDelegate {

    private final OrderRepository orderRepository;
    private final ShippingDispatcher shippingDispatcher;

    /**
     * Atomically claim a PAID order (PAID → AWAITING_SHIPPING) and dispatch shipping.
     *
     * <p>Runs in its own REQUIRES_NEW transaction so the JPA/Hibernate optimistic-lock flush
     * happens at method exit — letting the calling tasklet's per-order try/catch on
     * {@link org.springframework.dao.OptimisticLockingFailureException} actually observe a
     * race-loss against the {@code OrderPaidEvent} listener path.</p>
     *
     * @param order   the PAID order to claim — only its UUID is used; the order is re-loaded in
     *                this transaction and the caller's instance is never modified.
     * @param context the shipping payload to dispatch once the claim has been persisted.
     * @return {@code true} if this call actually dispatched to the carrier, {@code false} if the
     *         order was skipped because it is no longer PAID or {@code shippedAt} was already set (see below) — the caller
     *         must not send a shipping-confirmation notification for a skipped order.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claimAndShip(final OrderEntity order, final ShippingContext context) {
        // The tasklet's Spring Batch step runs inside a chunk transaction, so the `order` it hands
        // over is managed by THAT outer session. Mutating it here used to leave it dirty there at
        // its original @Version: once this REQUIRES_NEW commit bumped the version, the step's own
        // commit flushed the stale copy (UPDATE ... WHERE version=?) and failed the whole step with
        // ObjectOptimisticLockingFailureException. Work on a copy loaded in THIS transaction instead
        // and leave the caller's entity untouched.
        final OrderEntity current = orderRepository.findByUuid(order.getUuid())
                .orElseThrow(() -> new OrderNotFoundException("Order " + order.getUuid() + " not found"));

        // Re-read state, so a claim already committed by ShippingListener (on OrderPaidEvent) is
        // seen here as a non-PAID status; a claim still in flight is caught by @Version at commit.
        if (current.getStatus() != OrderStatus.PAID) {
            log.debug("Order {} is {} — already claimed by another path, skipping.", current.getUuid(), current.getStatus());
            return false;
        }

        // Guards the same narrow window as ShippingListener's identical check: updateOrder can
        // regress an AWAITING_SHIPPING order back to AWAITING_PAYMENT to fund a top-up, then the
        // payment webhook re-promotes it to PAID — which is exactly the status this tasklet polls
        // for (findByStatus(PAID)). shippedAt is the durable marker that stops a second, real
        // dispatch call to the carrier for an order this same delegate (or ShippingListener)
        // already shipped once.
        if (current.getShippedAt() != null) {
            log.info("Order {} was already handed to the carrier at {} — skipping (re-promoted to PAID by a later update).", current.getUuid(), current.getShippedAt());
            return false;
        }

        // Atomic claim: PAID -> AWAITING_SHIPPING, shippedAt stamped in the same save. The
        // optimistic-lock UPDATE (... WHERE version=?) detects a concurrent claim by the
        // OrderPaidEvent listener; the loser throws OptimisticLockingFailureException at this
        // REQUIRES_NEW commit, observed by the caller.
        current.setStatus(OrderStatus.AWAITING_SHIPPING);
        current.setShippedAt(LocalDateTime.now());
        orderRepository.save(current);

        shippingDispatcher.dispatch(context);

        current.setStatus(OrderStatus.SHIPPED);
        orderRepository.save(current);
        return true;
    }
}
