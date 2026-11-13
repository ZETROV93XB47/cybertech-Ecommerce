package com.novatech.cybertech.services.core;

import com.novatech.cybertech.events.PaymentFailedEvent;
import com.novatech.cybertech.events.PaymentRefundedEvent;
import com.novatech.cybertech.events.PaymentSucceededEvent;

/**
 * Transactional inner half of {@code OrderPaymentConfirmationEventListener}.
 *
 * <h2>Why this is a separate bean</h2>
 * Each handler here races the SAME {@code OrderEntity} row against a concurrent user-initiated
 * {@code cancelOrder}/{@code updateOrder} — {@code OrderCancellationTransactionalDelegate}'s own
 * javadoc documents this exact collision (the listener bumps the JPA {@code @Version} after a
 * {@code placeOrder} commit; a fast {@code POST /cancel} can collide with it). {@code cancelOrder}
 * survives that race via {@code @Retryable} on a non-transactional outer method that re-invokes a
 * separate bean on every attempt. This listener used to have NO such protection: a lost race meant
 * {@code orderRepository.save(order)} threw {@code OptimisticLockingFailureException} with nothing
 * to catch it — the payment confirmation was silently dropped, and Stripe never redelivers (this
 * listener only runs {@code AFTER_COMMIT} of the webhook's own transaction, which already returned
 * 200 to Stripe).
 *
 * <p>The fix mirrors {@code OrderCancellationTransactionalDelegate}: {@code @Retryable} sits on the
 * outer, non-transactional listener method; the retried unit of work lives here, on a separate
 * bean, so every retry attempt gets a genuinely fresh transaction and re-reads the order.
 *
 * <p>Each method here ALSO orders its writes so the DB-level order-status save (and, for a
 * successful payment, the cart clear) happens BEFORE the stock-service call — {@code commitStock}/
 * {@code releaseStock} end with a Redis write that is NOT part of this transaction. If the order
 * save were to run first, and it throws (the race above), nothing has touched Redis yet and the
 * whole transaction rolls back cleanly. Reversing that order (as the original code did) meant a
 * failed save left a permanently-deleted Redis TTL sentinel behind for a reservation the DB had
 * just rolled back back to ACTIVE/COMMITTED.
 */
public interface OrderPaymentConfirmationTransactionalDelegate {

    void handlePaymentSuccessWithinTransaction(PaymentSucceededEvent event);

    void handlePaymentFailedWithinTransaction(PaymentFailedEvent event);

    void handleRefundWithinTransaction(PaymentRefundedEvent event);
}
