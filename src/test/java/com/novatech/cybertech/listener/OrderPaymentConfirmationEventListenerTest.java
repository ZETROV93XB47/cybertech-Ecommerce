package com.novatech.cybertech.listener;

import com.novatech.cybertech.dto.request.stripe.StripeWebhookEventDto;
import com.novatech.cybertech.events.PaymentFailedEvent;
import com.novatech.cybertech.events.PaymentRefundedEvent;
import com.novatech.cybertech.events.PaymentSucceededEvent;
import com.novatech.cybertech.fixtures.dto.PaymentDtoFixtures;
import com.novatech.cybertech.services.core.OrderPaymentConfirmationTransactionalDelegate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Retryable;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link OrderPaymentConfirmationEventListener}.
 *
 * <p>The listener is now a thin {@code @Retryable}/{@code @TransactionalEventListener} wrapper —
 * all business logic (state guards, stock side-effects, write ordering) moved to
 * {@link OrderPaymentConfirmationTransactionalDelegate}, tested in
 * {@code OrderPaymentConfirmationTransactionalDelegateImpTest}. This class only verifies
 * delegation and that the annotations survived the refactor.
 */
@ExtendWith(MockitoExtension.class)
class OrderPaymentConfirmationEventListenerTest {

    @Mock
    private OrderPaymentConfirmationTransactionalDelegate delegate;

    @InjectMocks
    private OrderPaymentConfirmationEventListener listener;

    private static StripeWebhookEventDto eventForOrder(final UUID orderUuid) {
        final StripeWebhookEventDto event = PaymentDtoFixtures.aValidPaymentSucceededEvent();
        event.getData().getPaymentIntentPayload().getMetadata().put("order_uuid", orderUuid.toString());
        return event;
    }

    @Test
    void handlePaymentSuccess_delegatesToTransactionalDelegate() {
        final PaymentSucceededEvent event = new PaymentSucceededEvent(eventForOrder(UUID.randomUUID()));

        listener.handlePaymentSuccess(event);

        verify(delegate).handlePaymentSuccessWithinTransaction(event);
    }

    @Test
    void handlePaymentFailed_delegatesToTransactionalDelegate() {
        final PaymentFailedEvent event = new PaymentFailedEvent(eventForOrder(UUID.randomUUID()));

        listener.handlePaymentFailed(event);

        verify(delegate).handlePaymentFailedWithinTransaction(event);
    }

    @Test
    void handleRefund_delegatesToTransactionalDelegate() {
        final PaymentRefundedEvent event = new PaymentRefundedEvent(eventForOrder(UUID.randomUUID()));

        listener.handleRefund(event);

        verify(delegate).handleRefundWithinTransaction(event);
    }

    // ---------- annotation contracts ----------

    @Test
    void handlePaymentSuccessIsAnnotatedTransactionalEventListenerAfterCommit() throws NoSuchMethodException {
        final Method m = OrderPaymentConfirmationEventListener.class.getMethod("handlePaymentSuccess", PaymentSucceededEvent.class);
        final TransactionalEventListener ann = m.getAnnotation(TransactionalEventListener.class);
        assertThat(ann).isNotNull();
        assertThat(ann.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    }

    @Test
    void handlePaymentFailedIsAnnotatedTransactionalEventListenerAfterCommit() throws NoSuchMethodException {
        final Method m = OrderPaymentConfirmationEventListener.class.getMethod("handlePaymentFailed", PaymentFailedEvent.class);
        final TransactionalEventListener ann = m.getAnnotation(TransactionalEventListener.class);
        assertThat(ann).isNotNull();
        assertThat(ann.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    }

    @Test
    void handleRefundIsAnnotatedTransactionalEventListenerAfterCommit() throws NoSuchMethodException {
        final Method m = OrderPaymentConfirmationEventListener.class.getMethod("handleRefund", PaymentRefundedEvent.class);
        final TransactionalEventListener ann = m.getAnnotation(TransactionalEventListener.class);
        assertThat(ann).isNotNull();
        assertThat(ann.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    }

    /**
     * Regression guard for the bug documented in progress.md: this listener's write races
     * cancelOrder/updateOrder for the same order row, but used to have no retry at all — a lost
     * race silently dropped the payment confirmation (Stripe never redelivers an AFTER_COMMIT
     * listener's failure). Each handler must retry on {@link OptimisticLockingFailureException}.
     */
    @Test
    void allHandlers_retryOnOptimisticLockingFailure() throws NoSuchMethodException {
        for (final Method m : new Method[]{
                OrderPaymentConfirmationEventListener.class.getMethod("handlePaymentSuccess", PaymentSucceededEvent.class),
                OrderPaymentConfirmationEventListener.class.getMethod("handlePaymentFailed", PaymentFailedEvent.class),
                OrderPaymentConfirmationEventListener.class.getMethod("handleRefund", PaymentRefundedEvent.class)}) {
            final Retryable retryable = m.getAnnotation(Retryable.class);
            assertThat(retryable).as("%s should be @Retryable", m.getName()).isNotNull();
            assertThat(retryable.retryFor()).contains((Class) OptimisticLockingFailureException.class);
            assertThat(retryable.maxAttempts()).isEqualTo(3);
        }
    }
}
