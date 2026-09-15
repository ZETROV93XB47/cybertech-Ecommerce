package com.novatech.cybertech.services.implementation.order;

import com.novatech.cybertech.entities.OrderEntity;
import com.novatech.cybertech.entities.PaymentEntity;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.entities.enums.OrderStatus;
import com.novatech.cybertech.entities.enums.PaymentAttemptStatus;
import com.novatech.cybertech.entities.enums.PaymentType;
import com.novatech.cybertech.entities.enums.TransactionType;
import com.novatech.cybertech.entities.valueObjects.CurrencyCode;
import com.novatech.cybertech.entities.valueObjects.Money;
import com.novatech.cybertech.exceptions.CannotCancelOrderException;
import com.novatech.cybertech.exceptions.OrderDoesntBelongsToUserException;
import com.novatech.cybertech.exceptions.OrderNotFoundException;
import com.novatech.cybertech.fixtures.builders.OrderEntityBuilder;
import com.novatech.cybertech.fixtures.builders.PaymentEntityBuilder;
import com.novatech.cybertech.fixtures.builders.UserEntityBuilder;
import com.novatech.cybertech.fixtures.dto.OrderDtoFixtures;
import com.novatech.cybertech.mappers.entity.OrderMapper;
import com.novatech.cybertech.repositories.OrderRepository;
import com.novatech.cybertech.services.core.PaymentService;
import com.novatech.cybertech.services.core.StockService;
import com.novatech.cybertech.services.implementation.OrderCancellationTransactionalDelegateImp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Mockito unit tests for {@link OrderCancellationTransactionalDelegateImp} — the transactional
 * inner half of {@code OrderManagementServiceImp#cancelOrder}.
 *
 * <p>Relocated from {@code OrderManagementServiceImpTest.CancelOrder} when the retry mechanism
 * was swapped from a hand-rolled {@code TransactionTemplate} loop to Spring Retry's
 * {@code @Retryable} on {@code cancelOrder}, which required moving the actual cancellation
 * work onto a separate bean (same reason {@code CartWriteTransactionalDelegateImp} exists: the
 * proxy backing {@code @Transactional}/{@code @Retryable} only fires on a call crossing a bean
 * boundary). The {@code @Transactional} annotation is a no-op under plain Mockito, so the body
 * runs verbatim.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderCancellationTransactionalDelegateImp — cancel read-modify-write")
class OrderCancellationTransactionalDelegateTest {

    @Mock OrderMapper orderMapper;
    @Mock OrderRepository orderRepository;
    @Mock PaymentService paymentService;
    @Mock StockService stockService;

    @InjectMocks OrderCancellationTransactionalDelegateImp delegate;

    Jwt jwt;
    String keycloakId;

    @BeforeEach
    void setUp() {
        keycloakId = "kc-" + UUID.randomUUID();
        jwt = mock(Jwt.class);
        lenient().when(jwt.getSubject()).thenReturn(keycloakId);

        lenient().when(orderMapper.mapFromEntityToResponseDto(any(OrderEntity.class)))
                .thenReturn(OrderDtoFixtures.aSampleOrderResponse());
    }

    private PaymentEntity paymentWith(final PaymentAttemptStatus status, final TransactionType transactionType,
                                       final PaymentType type, final Money amount, final LocalDateTime createdAt) {
        return PaymentEntityBuilder.aValidPaymentBuilder()
                .status(status)
                .transactionType(transactionType)
                .paymentType(type)
                .amount(amount)
                .createdAt(createdAt)
                .build();
    }

    @Test
    @DisplayName("happy path: status flipped to CANCELED and refunds only SUCCESS+PAYMENT attempts")
    void happy_refundsOnlySuccessPayment_ignoresFailedAndRefund() {
        final UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).build();
        final PaymentEntity successPayment = paymentWith(PaymentAttemptStatus.SUCCESS, TransactionType.PAYMENT,
                PaymentType.VISA, new Money(new BigDecimal("100.00"), CurrencyCode.EUR), LocalDateTime.now());
        final PaymentEntity failedPayment = paymentWith(PaymentAttemptStatus.FAILED, TransactionType.PAYMENT,
                PaymentType.VISA, new Money(new BigDecimal("50.00"), CurrencyCode.EUR), LocalDateTime.now());
        final PaymentEntity refundEntry = paymentWith(PaymentAttemptStatus.SUCCESS, TransactionType.REFUND,
                PaymentType.VISA, new Money(new BigDecimal("20.00"), CurrencyCode.EUR), LocalDateTime.now());
        final List<PaymentEntity> attempts = new ArrayList<>(List.of(successPayment, failedPayment, refundEntry));

        final OrderEntity order = OrderEntityBuilder.aValidOrderBuilder()
                .userEntity(user)
                .status(OrderStatus.PAID)
                .paymentAttempts(attempts)
                .build();
        final UUID uuid = order.getUuid();
        when(orderRepository.findByUuid(uuid)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(OrderEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        delegate.cancelWithinTransaction(uuid, jwt);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        // Only one refund call (the SUCCESS+PAYMENT one).
        verify(paymentService, times(1)).refund(eq(order), eq(PaymentType.VISA),
                eq(successPayment.getAmount()), eq(successPayment.getIdempotencyKey()));
        verify(paymentService, times(1)).refund(any(), any(), any(), anyString());
    }

    @Test
    @DisplayName("already-shipped order throws CannotCancelOrderException")
    void alreadyShipped_throws() {
        final OrderEntity order = OrderEntityBuilder.aValidOrderBuilder()
                .userEntity(UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).build())
                .status(OrderStatus.SHIPPED)
                .build();
        when(orderRepository.findByUuid(order.getUuid())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> delegate.cancelWithinTransaction(order.getUuid(), jwt))
                .isInstanceOf(CannotCancelOrderException.class);

        verifyNoInteractions(paymentService);
    }

    @Test
    @DisplayName("delivered order also throws CannotCancelOrderException")
    void delivered_throws() {
        final OrderEntity order = OrderEntityBuilder.aValidOrderBuilder()
                .userEntity(UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).build())
                .status(OrderStatus.DELIVERED)
                .build();
        when(orderRepository.findByUuid(order.getUuid())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> delegate.cancelWithinTransaction(order.getUuid(), jwt))
                .isInstanceOf(CannotCancelOrderException.class);
    }

    @Test
    @DisplayName("order not found throws OrderNotFoundException")
    void notFound_throws() {
        final UUID missing = UUID.randomUUID();
        when(orderRepository.findByUuid(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> delegate.cancelWithinTransaction(missing, jwt))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    @DisplayName("wrong user throws OrderDoesntBelongsToUserException")
    void wrongUser_throws() {
        final UserEntity owner = UserEntityBuilder.aValidUserBuilder().keycloakId("someone-else").build();
        final OrderEntity order = OrderEntityBuilder.aValidOrderBuilder()
                .userEntity(owner).status(OrderStatus.PAID).build();
        when(orderRepository.findByUuid(order.getUuid())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> delegate.cancelWithinTransaction(order.getUuid(), jwt))
                .isInstanceOf(OrderDoesntBelongsToUserException.class);

        verifyNoInteractions(paymentService);
    }

    @Test
    @DisplayName("no successful payments: cancellation succeeds, no refund issued")
    void noSuccessfulPayments_noRefund() {
        final UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).build();
        final List<PaymentEntity> noneSuccess = new ArrayList<>(List.of(
                paymentWith(PaymentAttemptStatus.FAILED, TransactionType.PAYMENT, PaymentType.VISA,
                        new Money(new BigDecimal("10.00"), CurrencyCode.EUR), LocalDateTime.now())));
        final OrderEntity order = OrderEntityBuilder.aValidOrderBuilder()
                .userEntity(user).status(OrderStatus.AWAITING_PAYMENT).paymentAttempts(noneSuccess).build();
        when(orderRepository.findByUuid(order.getUuid())).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        delegate.cancelWithinTransaction(order.getUuid(), jwt);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        verifyNoInteractions(paymentService);
    }

    /**
     * BUG-1 FIX: AWAITING_SHIPPING used to satisfy {@code isOrderAlreadyShipped}'s
     * {@code >= SHIPPED} check by returning {@code false}, so cancellation went through, the
     * Stripe refund fired, but {@code stockService.releaseStock(uuid)} ran AFTER the async
     * commitStock listener — i.e. it was a no-op and the stock never came back. The fix
     * introduces {@code isCancellationLockedDueToShipping} which kicks in at AWAITING_SHIPPING
     * (code 5), refusing cancel before any refund/release work runs.
     */
    @Test
    @DisplayName("BUG-1 FIX: AWAITING_SHIPPING is now locked for cancel — CannotCancelOrderException, no refund")
    void cancelOrderShouldThrowWhenStatusIsAwaitingShipping() {
        final OrderEntity order = OrderEntityBuilder.aValidOrderBuilder()
                .userEntity(UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).build())
                .status(OrderStatus.AWAITING_SHIPPING)
                .paymentAttempts(new ArrayList<>(List.of(
                        paymentWith(PaymentAttemptStatus.SUCCESS, TransactionType.PAYMENT, PaymentType.VISA,
                                new Money(new BigDecimal("100.00"), CurrencyCode.EUR), LocalDateTime.now()))))
                .build();
        when(orderRepository.findByUuid(order.getUuid())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> delegate.cancelWithinTransaction(order.getUuid(), jwt))
                .isInstanceOf(CannotCancelOrderException.class);

        // Critical: the lock fires BEFORE any refund issues / stock release / save runs.
        verifyNoInteractions(paymentService);
        verify(stockService, never()).releaseStock(any(UUID.class));
        verify(orderRepository, never()).save(any());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.AWAITING_SHIPPING); // unchanged
    }

    @Test
    @DisplayName("regression: SHIPPED still throws CannotCancelOrderException")
    void cancelOrderShouldThrowWhenStatusIsShipped() {
        final OrderEntity order = OrderEntityBuilder.aValidOrderBuilder()
                .userEntity(UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).build())
                .status(OrderStatus.SHIPPED)
                .build();
        when(orderRepository.findByUuid(order.getUuid())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> delegate.cancelWithinTransaction(order.getUuid(), jwt))
                .isInstanceOf(CannotCancelOrderException.class);

        verifyNoInteractions(paymentService);
    }

    @Test
    @DisplayName("regression: PAID still cancels successfully (refunds the success payment)")
    void cancelOrderShouldSucceedWhenStatusIsPaid() {
        final UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).build();
        final PaymentEntity successPayment = paymentWith(PaymentAttemptStatus.SUCCESS, TransactionType.PAYMENT,
                PaymentType.VISA, new Money(new BigDecimal("75.00"), CurrencyCode.EUR), LocalDateTime.now());
        final OrderEntity order = OrderEntityBuilder.aValidOrderBuilder()
                .userEntity(user).status(OrderStatus.PAID)
                .paymentAttempts(new ArrayList<>(List.of(successPayment))).build();
        when(orderRepository.findByUuid(order.getUuid())).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        delegate.cancelWithinTransaction(order.getUuid(), jwt);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        verify(paymentService).refund(eq(order), eq(PaymentType.VISA), eq(successPayment.getAmount()), anyString());
    }

    @Test
    @DisplayName("regression: CREATED still cancels successfully (no payment yet, no refund issued)")
    void cancelOrderShouldSucceedWhenStatusIsCreated() {
        final UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).build();
        final OrderEntity order = OrderEntityBuilder.aValidOrderBuilder()
                .userEntity(user).status(OrderStatus.CREATED)
                .paymentAttempts(new ArrayList<>())
                .build();
        when(orderRepository.findByUuid(order.getUuid())).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        delegate.cancelWithinTransaction(order.getUuid(), jwt);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        verifyNoInteractions(paymentService);
    }
}
