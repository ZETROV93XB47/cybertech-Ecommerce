package com.novatech.cybertech.listener;

import com.novatech.cybertech.entities.OrderEntity;
import com.novatech.cybertech.entities.enums.OrderStatus;
import com.novatech.cybertech.events.PaymentFailedEvent;
import com.novatech.cybertech.events.PaymentRefundedEvent;
import com.novatech.cybertech.events.PaymentSucceededEvent;
import com.novatech.cybertech.exceptions.PaymentNotFoundException;
import com.novatech.cybertech.services.core.OrderPaymentConfirmationTransactionalDelegate;
import com.novatech.cybertech.services.core.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

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
 * or {@link OrderStatus#REFUNDED} respectively. The actual work lives on
 * {@link OrderPaymentConfirmationTransactionalDelegate} — see its javadoc for why.
 *
 * <p>WHY {@link TransactionalEventListener} with {@link TransactionPhase#AFTER_COMMIT}: the
 * Stripe webhook's outer transaction must commit (so the {@code PaymentEntity} row exists)
 * before we touch order status / stock; otherwise a rollback in the producer would leave us
 * having shipped, refunded, or released stock for a payment that never persisted.
 *
 * <p>WHY {@link Retryable}: this listener's write races the SAME {@code OrderEntity} row against
 * a concurrent user-initiated {@code cancelOrder}/{@code updateOrder} — see
 * {@link OrderPaymentConfirmationTransactionalDelegate}'s javadoc. Bounding retries to 3 attempts
 * on {@link OptimisticLockingFailureException}, each re-invoking the delegate (a separate bean —
 * required for both {@code @Retryable} and {@code @Transactional} to actually fire on every
 * attempt), absorbs that race instead of silently dropping the payment confirmation.
 *
 * <p>Missing {@code order_uuid} metadata is translated to a domain-level
 * {@link PaymentNotFoundException} by the delegate instead of a raw NPE.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPaymentConfirmationEventListener {

    private final OrderPaymentConfirmationTransactionalDelegate delegate;

    @Async(APPLICATION_ASYNC_TASK_EXECUTOR)
    @Retryable(retryFor = OptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 50))
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePaymentSuccess(final PaymentSucceededEvent event) {
        delegate.handlePaymentSuccessWithinTransaction(event);
    }

    @Async(APPLICATION_ASYNC_TASK_EXECUTOR)
    @Retryable(retryFor = OptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 50))
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePaymentFailed(final PaymentFailedEvent event) {
        delegate.handlePaymentFailedWithinTransaction(event);
    }

    @Async(APPLICATION_ASYNC_TASK_EXECUTOR)
    @Retryable(retryFor = OptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 50))
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleRefund(final PaymentRefundedEvent event) {
        delegate.handleRefundWithinTransaction(event);
    }
}
