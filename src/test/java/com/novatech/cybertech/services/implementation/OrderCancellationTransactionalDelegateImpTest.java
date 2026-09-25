package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.dto.response.order.OrderResponseDto;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OrderCancellationTransactionalDelegateImp}.
 *
 * <p>Regression coverage: {@code isCancellationLockedDueToShipping} used to compare
 * {@code status.getCode() >= AWAITING_SHIPPING.getCode()}, which also matched {@code CANCELED}
 * (9) and {@code REFUNDED} (10) — so cancelling an already-cancelled order threw a false
 * "already shipped" error instead of the documented idempotent return. The fix reorders the
 * idempotency check ahead of the shipping-lock check and bounds the lock to the actual
 * in-fulfillment statuses via an explicit {@code EnumSet}.
 */
@ExtendWith(MockitoExtension.class)
class OrderCancellationTransactionalDelegateImpTest {

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private PaymentService paymentService;
    @Mock
    private StockService stockService;

    private OrderCancellationTransactionalDelegateImp delegate;

    private String keycloakId;
    private Jwt jwt;

    @BeforeEach
    void setUp() {
        delegate = new OrderCancellationTransactionalDelegateImp(orderMapper, orderRepository, paymentService, stockService);
        keycloakId = "kc-" + UUID.randomUUID();
        jwt = mock(Jwt.class);
        lenient().when(jwt.getSubject()).thenReturn(keycloakId);
        lenient().when(orderMapper.mapFromEntityToResponseDto(any(OrderEntity.class)))
                .thenReturn(OrderDtoFixtures.aSampleOrderResponse());
    }

    private OrderEntity orderWithStatus(final OrderStatus status) {
        final UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).build();
        return OrderEntityBuilder.aValidOrderBuilder()
                .userEntity(user)
                .status(status)
                .totalAmount(new Money(java.math.BigDecimal.TEN, CurrencyCode.EUR))
                .paymentAttempts(new java.util.ArrayList<>())
                .build();
    }

    @Test
    @DisplayName("order not found -> OrderNotFoundException")
    void orderNotFound_throws() {
        final UUID orderUuid = UUID.randomUUID();
        when(orderRepository.findByUuid(orderUuid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> delegate.cancelWithinTransaction(orderUuid, jwt))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    @DisplayName("wrong user -> OrderDoesntBelongsToUserException, before any status check")
    void wrongUser_throws() {
        final OrderEntity order = orderWithStatus(OrderStatus.SHIPPED); // would also fail the shipping lock
        when(orderRepository.findByUuid(order.getUuid())).thenReturn(Optional.of(order));
        final Jwt otherUserJwt = mock(Jwt.class);
        lenient().when(otherUserJwt.getSubject()).thenReturn("someone-else");

        assertThatThrownBy(() -> delegate.cancelWithinTransaction(order.getUuid(), otherUserJwt))
                .isInstanceOf(OrderDoesntBelongsToUserException.class);

        verifyNoInteractions(paymentService, stockService, orderMapper);
    }

    @Nested
    @DisplayName("Idempotent re-cancel")
    class Idempotency {

        @ParameterizedTest
        @EnumSource(value = OrderStatus.class, names = {"CANCELED", "REFUNDED"})
        @DisplayName("order already CANCELED/REFUNDED -> returns current state, no exception, no side effects")
        void alreadyTerminal_returnsCurrentStateWithoutError(final OrderStatus status) {
            final OrderEntity order = orderWithStatus(status);
            when(orderRepository.findByUuid(order.getUuid())).thenReturn(Optional.of(order));

            final OrderResponseDto result = delegate.cancelWithinTransaction(order.getUuid(), jwt);

            assertThat(result).isNotNull();
            assertThat(order.getStatus()).isEqualTo(status); // untouched
            verifyNoInteractions(paymentService, stockService);
            verify(orderRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Shipping lock")
    class ShippingLock {

        @ParameterizedTest
        @EnumSource(value = OrderStatus.class, names = {"AWAITING_SHIPPING", "SHIPPED", "DELIVERED", "RETURNED"})
        @DisplayName("order in fulfillment (not CANCELED/REFUNDED) -> CannotCancelOrderException")
        void inFulfillment_throwsCannotCancel(final OrderStatus status) {
            final OrderEntity order = orderWithStatus(status);
            when(orderRepository.findByUuid(order.getUuid())).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> delegate.cancelWithinTransaction(order.getUuid(), jwt))
                    .isInstanceOf(CannotCancelOrderException.class);

            verifyNoInteractions(paymentService, stockService);
            verify(orderRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("PAID order -> CANCELED, successful PAYMENT attempts refunded, stock released")
        void cancelsPaidOrder() {
            final OrderEntity order = orderWithStatus(OrderStatus.PAID);
            final PaymentEntity successfulPayment = PaymentEntityBuilder.aValidPaymentBuilder()
                    .status(PaymentAttemptStatus.SUCCESS)
                    .transactionType(TransactionType.PAYMENT)
                    .paymentType(PaymentType.VISA)
                    .amount(new Money(java.math.BigDecimal.TEN, CurrencyCode.EUR))
                    .createdAt(LocalDateTime.now())
                    .build();
            order.setPaymentAttempts(List.of(successfulPayment));
            when(orderRepository.findByUuid(order.getUuid())).thenReturn(Optional.of(order));
            when(orderRepository.save(order)).thenReturn(order);
            when(paymentService.refund(order, successfulPayment.getPaymentType(),
                    successfulPayment.getAmount(), successfulPayment.getIdempotencyKey()))
                    .thenReturn(PaymentEntityBuilder.aValidPaymentBuilder()
                            .status(PaymentAttemptStatus.SUCCESS)
                            .transactionType(TransactionType.REFUND)
                            .build());

            final OrderResponseDto result = delegate.cancelWithinTransaction(order.getUuid(), jwt);

            assertThat(result).isNotNull();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
            verify(paymentService).refund(order, successfulPayment.getPaymentType(),
                    successfulPayment.getAmount(), successfulPayment.getIdempotencyKey());
            verify(stockService).releaseStock(order.getUuid());
            verify(orderRepository).save(order);
        }

        @Test
        @DisplayName("Stripe rejects the refund -> OrderRefundFailedException, order left unchanged, stock untouched")
        void refundRejected_abortsCancellationWithoutMutatingOrder() {
            final OrderEntity order = orderWithStatus(OrderStatus.PAID);
            final PaymentEntity successfulPayment = PaymentEntityBuilder.aValidPaymentBuilder()
                    .status(PaymentAttemptStatus.SUCCESS)
                    .transactionType(TransactionType.PAYMENT)
                    .paymentType(PaymentType.VISA)
                    .amount(new Money(java.math.BigDecimal.TEN, CurrencyCode.EUR))
                    .createdAt(LocalDateTime.now())
                    .build();
            order.setPaymentAttempts(List.of(successfulPayment));
            when(orderRepository.findByUuid(order.getUuid())).thenReturn(Optional.of(order));
            when(paymentService.refund(order, successfulPayment.getPaymentType(),
                    successfulPayment.getAmount(), successfulPayment.getIdempotencyKey()))
                    .thenReturn(PaymentEntityBuilder.aValidPaymentBuilder()
                            .status(PaymentAttemptStatus.FAILED)
                            .transactionType(TransactionType.REFUND)
                            .build());

            assertThatThrownBy(() -> delegate.cancelWithinTransaction(order.getUuid(), jwt))
                    .isInstanceOf(com.novatech.cybertech.exceptions.OrderRefundFailedException.class);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
            verifyNoInteractions(stockService);
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("only SUCCESS/PAYMENT attempts are refunded — FAILED and REFUND-type attempts are skipped")
        void onlyRefundsSuccessfulPaymentAttempts() {
            final OrderEntity order = orderWithStatus(OrderStatus.PAID);
            final PaymentEntity failedPayment = PaymentEntityBuilder.aValidPaymentBuilder()
                    .status(PaymentAttemptStatus.FAILED)
                    .transactionType(TransactionType.PAYMENT)
                    .build();
            final PaymentEntity priorRefund = PaymentEntityBuilder.aValidPaymentBuilder()
                    .status(PaymentAttemptStatus.SUCCESS)
                    .transactionType(TransactionType.REFUND)
                    .build();
            order.setPaymentAttempts(List.of(failedPayment, priorRefund));
            when(orderRepository.findByUuid(order.getUuid())).thenReturn(Optional.of(order));
            when(orderRepository.save(order)).thenReturn(order);

            delegate.cancelWithinTransaction(order.getUuid(), jwt);

            verifyNoInteractions(paymentService);
            verify(stockService).releaseStock(order.getUuid());
        }
    }
}
