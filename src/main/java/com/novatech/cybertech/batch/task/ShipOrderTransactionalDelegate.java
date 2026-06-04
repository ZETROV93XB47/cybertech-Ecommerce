package com.novatech.cybertech.batch.task;

import com.novatech.cybertech.dispatcher.ShippingDispatcher;
import com.novatech.cybertech.dto.data.ShippingContext;
import com.novatech.cybertech.entities.OrderEntity;
import com.novatech.cybertech.entities.enums.OrderStatus;
import com.novatech.cybertech.repositories.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Per-order claim+dispatch helper invoked by {@link ShipAllPaidOrdersTasklet} in its own
 * {@link Propagation#REQUIRES_NEW} transaction.
 *
 * <p>Wave 3 regression-fix: the previous implementation kept everything inside the tasklet's
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
     * @param order   the PAID order being claimed (passed by reference so the caller can
     *                still inspect the in-memory status after the call returns).
     * @param context the shipping payload to dispatch once the claim has been persisted.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void claimAndShip(final OrderEntity order, final ShippingContext context) {
        // Atomic claim: PAID -> AWAITING_SHIPPING. The optimistic-lock UPDATE (... WHERE version=?)
        // is what detects a concurrent claim by the OrderPaidEvent listener; the loser throws
        // OptimisticLockingFailureException at this REQUIRES_NEW commit, observed by the caller.
        order.setStatus(OrderStatus.AWAITING_SHIPPING);
        final OrderEntity claimed = orderRepository.save(order);

        shippingDispatcher.dispatch(context);

        // Persist the terminal SHIPPED status on the SAME managed entity, inside THIS transaction.
        // Previously the tasklet did this on the detached `order` AFTER the REQUIRES_NEW commit had
        // already bumped @Version — the stale-version save then failed with
        // OptimisticLockingFailureException and the order never reached SHIPPED in the DB.
        claimed.setStatus(OrderStatus.SHIPPED);
        orderRepository.save(claimed);
    }
}
