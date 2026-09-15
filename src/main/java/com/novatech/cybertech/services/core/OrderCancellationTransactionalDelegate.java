package com.novatech.cybertech.services.core;

import com.novatech.cybertech.dto.response.order.OrderResponseDto;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

/**
 * Transactional inner half of {@code OrderManagementServiceImp#cancelOrder}.
 *
 * <h2>Why this is a separate bean</h2>
 * Cancelling an order can race the async {@code @TransactionalEventListener(AFTER_COMMIT)}
 * listener that flips a just-paid order to {@code PAID} — both write the same
 * {@code OrderEntity} row, and {@code @Version} optimistic locking is what detects the conflict.
 * Recovering means re-reading the order and retrying with fresh state, which
 * {@code @Retryable} (Spring Retry) handles declaratively — but only if each retry attempt gets a
 * genuinely fresh transaction with a fresh entity snapshot.
 *
 * <p>Both {@code @Transactional} and {@code @Retryable} are applied via Spring AOP proxies that
 * only fire on a call crossing a bean boundary — a self-invoked private method would silently run
 * outside both. So the retried unit of work lives here, on a separate bean, while
 * {@code @Retryable} sits on the outer, non-transactional {@code cancelOrder} method that calls
 * it: each retry re-enters this bean's proxy, starting a brand-new transaction that re-reads the
 * order. Mirrors the {@code CartWriteTransactionalDelegate} split (there: lock vs. commit
 * ordering; here: retry vs. fresh-transaction-per-attempt) — same root cause, different
 * cross-cutting concern.
 */
public interface OrderCancellationTransactionalDelegate {

    /**
     * Performs the cancellation read-modify-write inside its own transaction: shipping-lock and
     * ownership guards, refund of any successful payment, stock release, and the {@code CANCELED}
     * save.
     *
     * <p>Idempotent — returns the current snapshot without re-running any side effect if the
     * order is already {@code CANCELED} (e.g. a previous retry attempt already committed the
     * cancellation).
     *
     * @param orderUUID order to cancel.
     * @param jwt       caller identity.
     * @return the updated (or already-canceled) order DTO.
     */
    OrderResponseDto cancelWithinTransaction(UUID orderUUID, Jwt jwt);
}
