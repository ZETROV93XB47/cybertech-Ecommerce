package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.dto.request.stripe.StripeWebhookEventDto;
import com.novatech.cybertech.entities.OrderEntity;
import com.novatech.cybertech.entities.PaymentEntity;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.entities.enums.OrderStatus;
import com.novatech.cybertech.entities.enums.PaymentAttemptStatus;
import com.novatech.cybertech.entities.enums.TransactionType;
import com.novatech.cybertech.entities.valueObjects.CurrencyCode;
import com.novatech.cybertech.entities.valueObjects.Money;
import com.novatech.cybertech.events.PaymentFailedEvent;
import com.novatech.cybertech.events.PaymentRefundedEvent;
import com.novatech.cybertech.events.PaymentSucceededEvent;
import com.novatech.cybertech.exceptions.PaymentNotFoundException;
import com.novatech.cybertech.fixtures.builders.OrderEntityBuilder;
import com.novatech.cybertech.fixtures.builders.PaymentEntityBuilder;
import com.novatech.cybertech.fixtures.builders.UserEntityBuilder;
import com.novatech.cybertech.fixtures.dto.PaymentDtoFixtures;
import com.novatech.cybertech.repositories.OrderRepository;
import com.novatech.cybertech.services.core.CartService;
import com.novatech.cybertech.services.core.StockService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OrderPaymentConfirmationTransactionalDelegateImp}.
 *
 * <p>Migrated from the old {@code OrderPaymentConfirmationEventListenerTest} (the business logic
 * moved here — see the delegate's javadoc), plus new ordering tests for the fix: the order-status
 * save (and cart clear, for a success) must happen BEFORE the stock-service call, so an
 * {@code OptimisticLockingFailureException} on the save can't leave a deleted Redis TTL sentinel
 * behind for a reservation the rolled-back transaction still considers live.
 */
@ExtendWith(MockitoExtension.class)
class OrderPaymentConfirmationTransactionalDelegateImpTest {

    @Mock
    private CartService cartService;
    @Mock
    private StockService stockService;
    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderPaymentConfirmationTransactionalDelegateImp delegate;

    private static StripeWebhookEventDto eventForOrder(final UUID orderUuid) {
        final StripeWebhookEventDto event = PaymentDtoFixtures.aValidPaymentSucceededEvent();
        event.getData().getPaymentIntentPayload().getMetadata().put("order_uuid", orderUuid.toString());
        return event;
    }

    private static OrderEntity orderWithKeycloakId(final UUID uuid, final String keycloakId) {
        final UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).build();
        return OrderEntityBuilder.aValidOrderBuilder().uuid(uuid).userEntity(user).build();
    }

    private static OrderEntity orderWithStatus(final UUID uuid, final OrderStatus status) {
        final UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId("kc-status").build();
        return OrderEntityBuilder.aValidOrderBuilder().uuid(uuid).userEntity(user).status(status).build();
    }

    private static PaymentEntity successfulAttempt(final TransactionType transactionType, final BigDecimal amount) {
        return PaymentEntityBuilder.aValidPaymentBuilder()
                .transactionType(transactionType)
                .status(PaymentAttemptStatus.SUCCESS)
                .amount(new Money(amount, CurrencyCode.EUR))
                .build();
    }

    // ---------- happy paths ----------

    @Test
    void handlePaymentSuccessShouldCommitStockMarkPaidAndClearCart() {
        final UUID uuid = UUID.randomUUID();
        final OrderEntity order = orderWithKeycloakId(uuid, "kc-1");
        when(orderRepository.findByUuid(uuid)).thenReturn(Optional.of(order));

        delegate.handlePaymentSuccessWithinTransaction(new PaymentSucceededEvent(eventForOrder(uuid)));

        verify(stockService).commitStock(uuid);
        final ArgumentCaptor<OrderEntity> cap = ArgumentCaptor.forClass(OrderEntity.class);
        verify(orderRepository).save(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo(OrderStatus.PAID);
        verify(cartService).clearCart("kc-1");
    }

    @Test
    void handlePaymentSuccess_savesAndClearsCartBeforeTouchingStock() {
        // The fix: order.save + cartService.clearCart (both DB-transactional) must happen BEFORE
        // stockService.commitStock (whose Redis write is NOT part of this transaction).
        final UUID uuid = UUID.randomUUID();
        final OrderEntity order = orderWithKeycloakId(uuid, "kc-order");
        when(orderRepository.findByUuid(uuid)).thenReturn(Optional.of(order));

        delegate.handlePaymentSuccessWithinTransaction(new PaymentSucceededEvent(eventForOrder(uuid)));

        final InOrder ord = inOrder(orderRepository, cartService, stockService);
        ord.verify(orderRepository).save(any(OrderEntity.class));
        ord.verify(cartService).clearCart("kc-order");
        ord.verify(stockService).commitStock(uuid);
    }

    @Test
    void handlePaymentSuccessShouldThrowWhenOrderNotFound() {
        final UUID uuid = UUID.randomUUID();
        when(orderRepository.findByUuid(uuid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> delegate.handlePaymentSuccessWithinTransaction(new PaymentSucceededEvent(eventForOrder(uuid))))
                .isInstanceOf(PaymentNotFoundException.class)
                .hasMessageContaining(uuid.toString());
        verifyNoInteractions(stockService, cartService);
    }

    @Test
    void handlePaymentFailedShouldReleaseStockAndMarkPaymentFailed() {
        final UUID uuid = UUID.randomUUID();
        final OrderEntity order = orderWithKeycloakId(uuid, "kc-2");
        when(orderRepository.findByUuid(uuid)).thenReturn(Optional.of(order));

        delegate.handlePaymentFailedWithinTransaction(new PaymentFailedEvent(eventForOrder(uuid)));

        verify(stockService).releaseStock(uuid);
        final ArgumentCaptor<OrderEntity> cap = ArgumentCaptor.forClass(OrderEntity.class);
        verify(orderRepository).save(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        verifyNoInteractions(cartService);
    }

    @Test
    void handlePaymentFailed_savesBeforeTouchingStock() {
        final UUID uuid = UUID.randomUUID();
        final OrderEntity order = orderWithKeycloakId(uuid, "kc-order");
        when(orderRepository.findByUuid(uuid)).thenReturn(Optional.of(order));

        delegate.handlePaymentFailedWithinTransaction(new PaymentFailedEvent(eventForOrder(uuid)));

        final InOrder ord = inOrder(orderRepository, stockService);
        ord.verify(orderRepository).save(any(OrderEntity.class));
        ord.verify(stockService).releaseStock(uuid);
    }

    @Test
    void handleRefundShouldReleaseStockAndMarkRefunded() {
        final UUID uuid = UUID.randomUUID();
        // A refund only applies to an order that actually got paid (or was canceled awaiting refund).
        final OrderEntity order = orderWithStatus(uuid, OrderStatus.PAID);
        // Full refund: a single PAYMENT/SUCCESS of 100 fully offset by a REFUND/SUCCESS of 100
        // already recorded for this refund -> netPaid == 0, so the order must be fully closed out.
        order.setPaymentAttempts(List.of(
                successfulAttempt(TransactionType.PAYMENT, new BigDecimal("100.00")),
                successfulAttempt(TransactionType.REFUND, new BigDecimal("100.00"))
        ));
        when(orderRepository.findByUuid(uuid)).thenReturn(Optional.of(order));

        delegate.handleRefundWithinTransaction(new PaymentRefundedEvent(eventForOrder(uuid)));

        verify(stockService).releaseStock(uuid);
        final ArgumentCaptor<OrderEntity> cap = ArgumentCaptor.forClass(OrderEntity.class);
        verify(orderRepository).save(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo(OrderStatus.REFUNDED);
        verifyNoInteractions(cartService);
    }

    @Test
    void handleRefund_savesBeforeTouchingStock() {
        final UUID uuid = UUID.randomUUID();
        final OrderEntity order = orderWithStatus(uuid, OrderStatus.PAID);
        // netPaid must be exactly zero (payment fully offset by the recorded refund) to take the
        // full-refund path — see handleRefund_onPartialRefund_shouldNotReleaseStockNorMarkRefunded.
        order.setPaymentAttempts(List.of(
                successfulAttempt(TransactionType.PAYMENT, new BigDecimal("100.00")),
                successfulAttempt(TransactionType.REFUND, new BigDecimal("100.00"))
        ));
        when(orderRepository.findByUuid(uuid)).thenReturn(Optional.of(order));

        delegate.handleRefundWithinTransaction(new PaymentRefundedEvent(eventForOrder(uuid)));

        final InOrder ord = inOrder(orderRepository, stockService);
        ord.verify(orderRepository).save(any(OrderEntity.class));
        ord.verify(stockService).releaseStock(uuid);
    }

    @Test
    void handleRefund_onPartialRefund_shouldNotReleaseStockNorMarkRefunded() {
        // The customer dropped one item from an already-paid order (OrderManagementServiceImp#updateOrder
        // refunds only the difference). The confirming charge.refunded webhook must NOT be treated as a
        // full refund: stock still reserved for the kept items must stay reserved, and the order must
        // keep its current (non-terminal) status.
        final UUID uuid = UUID.randomUUID();
        final OrderEntity order = orderWithStatus(uuid, OrderStatus.PAID);
        order.setPaymentAttempts(List.of(
                successfulAttempt(TransactionType.PAYMENT, new BigDecimal("100.00")),
                successfulAttempt(TransactionType.REFUND, new BigDecimal("30.00"))
        ));
        when(orderRepository.findByUuid(uuid)).thenReturn(Optional.of(order));

        delegate.handleRefundWithinTransaction(new PaymentRefundedEvent(eventForOrder(uuid)));

        verify(stockService, never()).releaseStock(any());
        verify(orderRepository, never()).save(any());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verifyNoInteractions(cartService);
    }

    // ---------- status guard: stale / out-of-order webhooks must not regress the order ----------

    @Test
    void handlePaymentSuccess_onCanceledOrder_isIgnored_noResurrection() {
        // A late payment_intent.succeeded arriving AFTER the user canceled (and got refunded)
        // must NOT resurrect the order to PAID.
        final UUID uuid = UUID.randomUUID();
        final OrderEntity order = orderWithStatus(uuid, OrderStatus.CANCELED);
        when(orderRepository.findByUuid(uuid)).thenReturn(Optional.of(order));

        delegate.handlePaymentSuccessWithinTransaction(new PaymentSucceededEvent(eventForOrder(uuid)));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(stockService, cartService);
    }

    @Test
    void handlePaymentSuccess_onRetryablePaymentFailedOrder_completesToPaid() {
        // retryPayment leaves the order in PAYMENT_FAILED; the success webhook must still complete it.
        final UUID uuid = UUID.randomUUID();
        final OrderEntity order = orderWithStatus(uuid, OrderStatus.PAYMENT_FAILED);
        when(orderRepository.findByUuid(uuid)).thenReturn(Optional.of(order));

        delegate.handlePaymentSuccessWithinTransaction(new PaymentSucceededEvent(eventForOrder(uuid)));

        verify(stockService).commitStock(uuid);
        verify(orderRepository).save(any());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void handlePaymentFailed_onPaidOrder_isIgnored_noRegression() {
        // A late payment_intent.payment_failed must NOT regress an already-PAID order.
        final UUID uuid = UUID.randomUUID();
        final OrderEntity order = orderWithStatus(uuid, OrderStatus.PAID);
        when(orderRepository.findByUuid(uuid)).thenReturn(Optional.of(order));

        delegate.handlePaymentFailedWithinTransaction(new PaymentFailedEvent(eventForOrder(uuid)));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(stockService, cartService);
    }

    @Test
    void handleRefund_onAlreadyRefundedOrder_isIgnored() {
        final UUID uuid = UUID.randomUUID();
        final OrderEntity order = orderWithStatus(uuid, OrderStatus.REFUNDED);
        when(orderRepository.findByUuid(uuid)).thenReturn(Optional.of(order));

        delegate.handleRefundWithinTransaction(new PaymentRefundedEvent(eventForOrder(uuid)));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(stockService, cartService);
    }

    @Test
    void handlePaymentFailedShouldThrowWhenOrderNotFound() {
        final UUID uuid = UUID.randomUUID();
        when(orderRepository.findByUuid(uuid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> delegate.handlePaymentFailedWithinTransaction(new PaymentFailedEvent(eventForOrder(uuid))))
                .isInstanceOf(PaymentNotFoundException.class)
                .hasMessageContaining(uuid.toString());
    }

    // ---------- no null-guard on metadata.order_uuid ----------

    @Test
    void handlePaymentSuccess_nullOrderUuidInMetadata_shouldThrowDomainError() {
        final StripeWebhookEventDto stripe = PaymentDtoFixtures.aValidPaymentSucceededEvent();
        stripe.getData().getPaymentIntentPayload().getMetadata().remove("order_uuid");
        // Missing order_uuid is translated to a domain-level PaymentNotFoundException
        // instead of a raw NullPointerException out of UUID.fromString(null).
        assertThatThrownBy(() -> delegate.handlePaymentSuccessWithinTransaction(new PaymentSucceededEvent(stripe)))
                .isInstanceOf(PaymentNotFoundException.class)
                .hasMessageContaining("order_uuid");
        verifyNoInteractions(stockService, cartService, orderRepository);
    }

    @Test
    void handlePaymentFailed_nullOrderUuidInMetadata_shouldThrowDomainError() {
        final StripeWebhookEventDto stripe = PaymentDtoFixtures.aValidPaymentSucceededEvent();
        stripe.getData().getPaymentIntentPayload().getMetadata().remove("order_uuid");
        assertThatThrownBy(() -> delegate.handlePaymentFailedWithinTransaction(new PaymentFailedEvent(stripe)))
                .isInstanceOf(PaymentNotFoundException.class)
                .hasMessageContaining("order_uuid");
        verifyNoInteractions(stockService, orderRepository);
    }

    @Test
    void handleRefund_nullOrderUuidInMetadata_shouldThrowDomainError() {
        final StripeWebhookEventDto stripe = PaymentDtoFixtures.aValidPaymentSucceededEvent();
        stripe.getData().getPaymentIntentPayload().getMetadata().remove("order_uuid");
        assertThatThrownBy(() -> delegate.handleRefundWithinTransaction(new PaymentRefundedEvent(stripe)))
                .isInstanceOf(PaymentNotFoundException.class)
                .hasMessageContaining("order_uuid");
        verifyNoInteractions(stockService, orderRepository);
    }

    // ---------- regression: a failed save must never reach the stock/Redis call ----------

    @Test
    void handlePaymentSuccess_saveThrows_stockServiceNeverCalled() {
        // Regression test for the bug documented in progress.md: if orderRepository.save throws
        // (e.g. OptimisticLockingFailureException racing a concurrent cancelOrder/updateOrder),
        // stockService (whose commitStock ends with a non-transactional Redis write) must never
        // be reached — otherwise a rolled-back save would leave a deleted Redis TTL sentinel
        // behind for a reservation the DB still considers live.
        final UUID uuid = UUID.randomUUID();
        final OrderEntity order = orderWithKeycloakId(uuid, "kc-race");
        when(orderRepository.findByUuid(uuid)).thenReturn(Optional.of(order));
        doThrow(new org.springframework.dao.OptimisticLockingFailureException("version conflict"))
                .when(orderRepository).save(any(OrderEntity.class));

        assertThatThrownBy(() -> delegate.handlePaymentSuccessWithinTransaction(new PaymentSucceededEvent(eventForOrder(uuid))))
                .isInstanceOf(org.springframework.dao.OptimisticLockingFailureException.class);

        verifyNoInteractions(stockService, cartService);
    }
}
