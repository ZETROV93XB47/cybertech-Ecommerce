package com.novatech.cybertech.services.implementation.order;

import com.novatech.cybertech.dto.data.OrderEventDto;
import com.novatech.cybertech.dto.data.OrderValidationDto;
import com.novatech.cybertech.dto.request.order.OrderPlacingRequestDto;
import com.novatech.cybertech.dto.request.order.OrderUpdateRequestDto;
import com.novatech.cybertech.dto.request.orderItem.OrderItemCreateRequestDto;
import com.novatech.cybertech.dto.response.order.OrderResponseDto;
import com.novatech.cybertech.entities.BankCardEntity;
import com.novatech.cybertech.entities.CartEntity;
import com.novatech.cybertech.entities.CartItemEntity;
import com.novatech.cybertech.entities.OrderEntity;
import com.novatech.cybertech.entities.OrderItemEntity;
import com.novatech.cybertech.entities.PaymentEntity;
import com.novatech.cybertech.entities.ProductEntity;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.entities.enums.DiscountType;
import com.novatech.cybertech.entities.enums.OrderStatus;
import com.novatech.cybertech.entities.enums.PaymentAttemptStatus;
import com.novatech.cybertech.entities.enums.PaymentType;
import com.novatech.cybertech.entities.enums.ShippingProvider;
import com.novatech.cybertech.entities.enums.ShippingType;
import com.novatech.cybertech.entities.enums.TransactionType;
import com.novatech.cybertech.entities.valueObjects.CurrencyCode;
import com.novatech.cybertech.entities.valueObjects.Money;
import com.novatech.cybertech.events.OrderCreatedEvent;
import com.novatech.cybertech.events.OrderUpdatedEvent;
import com.novatech.cybertech.exceptions.CannotCancelOrderException;
import com.novatech.cybertech.exceptions.CartNotFoundException;
import com.novatech.cybertech.exceptions.FailedRetryingPayment;
import com.novatech.cybertech.exceptions.NoDefaultBankCartSetException;
import com.novatech.cybertech.exceptions.NoPreviousPaymentAttemptException;
import com.novatech.cybertech.exceptions.NotEnoughStockException;
import com.novatech.cybertech.exceptions.OrderAlreadyShippedException;
import com.novatech.cybertech.exceptions.OrderDoesntBelongsToUserException;
import com.novatech.cybertech.exceptions.OrderNotFoundException;
import com.novatech.cybertech.exceptions.UserNotFoundException;
import com.novatech.cybertech.fixtures.builders.BankCardEntityBuilder;
import com.novatech.cybertech.fixtures.builders.CartEntityBuilder;
import com.novatech.cybertech.fixtures.builders.CartItemEntityBuilder;
import com.novatech.cybertech.fixtures.builders.OrderEntityBuilder;
import com.novatech.cybertech.fixtures.builders.OrderItemEntityBuilder;
import com.novatech.cybertech.fixtures.builders.PaymentEntityBuilder;
import com.novatech.cybertech.fixtures.builders.ProductEntityBuilder;
import com.novatech.cybertech.fixtures.builders.UserEntityBuilder;
import com.novatech.cybertech.fixtures.dto.OrderDtoFixtures;
import com.novatech.cybertech.mappers.entity.OrderMapper;
import com.novatech.cybertech.repositories.OrderRepository;
import com.novatech.cybertech.repositories.ProductRepository;
import com.novatech.cybertech.repositories.UserRepository;
import com.novatech.cybertech.services.core.IdempotencyKeyServiceGenerator;
import com.novatech.cybertech.services.core.PaymentService;
import com.novatech.cybertech.services.core.StockService;
import com.novatech.cybertech.services.implementation.OrderManagementServiceImp;
import com.novatech.cybertech.validator.core.OrderValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.oauth2.jwt.Jwt;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Mockito unit tests for {@link OrderManagementServiceImp}.
 *
 * <p><b>SA-W3.1 wave</b> — recreates SA3.1a's surface (50 tests in 7 nested groups) and re-verifies
 * the BUG-050 stock-release-on-payment-failure fix asserted by Wave F2 (2026-04-23). The brief
 * mentioned an {@code OrderPriceCalculationService} dependency added in Wave F4, but the actual
 * production source on this branch does NOT inject it — only the original 9 dependencies remain
 * ({@link OrderMapper}, {@link StockService}, {@link PaymentService}, {@link UserRepository},
 * {@link OrderRepository}, {@link ProductRepository}, {@link OrderValidator},
 * {@link ApplicationEventPublisher}, {@link IdempotencyKeyServiceGenerator}). Trusting the source
 * over the brief, we mock only the actual constructor deps.</p>
 */
@ExtendWith(MockitoExtension.class)
class OrderManagementServiceImpTest {

    @Mock OrderMapper orderMapper;
    @Mock StockService stockService;
    @Mock PaymentService paymentService;
    @Mock UserRepository userRepository;
    @Mock OrderRepository orderRepository;
    @Mock ProductRepository productRepository;
    @Mock OrderValidator orderValidator;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock IdempotencyKeyServiceGenerator idempotencyKeyService;

    @InjectMocks OrderManagementServiceImp service;

    Jwt jwt;
    String keycloakId;

    @BeforeEach
    void setUp() {
        keycloakId = "kc-" + UUID.randomUUID();
        jwt = mock(Jwt.class);
        lenient().when(jwt.getSubject()).thenReturn(keycloakId);

        // Both overloads stubbed lenient — placeOrder/updateOrder use (String,List<String>),
        // retryPayment uses (String,String).
        lenient().when(idempotencyKeyService.generateKey(anyString(), anyList()))
                .thenReturn("idem-key-list");
        lenient().when(idempotencyKeyService.generateKey(anyString(), anyString()))
                .thenReturn("idem-key-str");

        // Mapper just needs to return SOMETHING non-null when service maps the saved entity for the response.
        lenient().when(orderMapper.mapFromEntityToResponseDto(any(OrderEntity.class)))
                .thenReturn(OrderDtoFixtures.aSampleOrderResponse());
    }

    // ---- helpers --------------------------------------------------------

    private UserEntity userWithCart(final ProductEntity product, final int quantity, final BigDecimal unitPrice) {
        final UserEntity user = UserEntityBuilder.aValidUserBuilder()
                .keycloakId(keycloakId)
                .bankCardEntity(BankCardEntityBuilder.aValidBankCard())
                .build();
        final CartItemEntity item = CartItemEntityBuilder.aValidCartItemBuilder()
                .productEntity(product)
                .quantity(quantity)
                .unitPrice(unitPrice)
                .build();
        final CartEntity cart = CartEntityBuilder.aValidCartBuilder()
                .userEntity(user)
                .cartItems(new ArrayList<>(List.of(item)))
                .build();
        user.setCartEntity(cart);
        return user;
    }

    private UserEntity userWithEmptyCart() {
        final UserEntity user = UserEntityBuilder.aValidUserBuilder()
                .keycloakId(keycloakId)
                .bankCardEntity(BankCardEntityBuilder.aValidBankCard())
                .build();
        final CartEntity cart = CartEntityBuilder.aValidCartBuilder()
                .userEntity(user)
                .cartItems(new ArrayList<>())
                .build();
        user.setCartEntity(cart);
        return user;
    }

    private UserEntity userNoCart() {
        return UserEntityBuilder.aValidUserBuilder()
                .keycloakId(keycloakId)
                .bankCardEntity(BankCardEntityBuilder.aValidBankCard())
                .cartEntity(null)
                .build();
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

    private OrderResponseDto stubMappedResponse() {
        return OrderDtoFixtures.aSampleOrderResponse();
    }

    // =================================================================
    @Nested
    @DisplayName("placeOrder")
    class PlaceOrder {

        @Test
        @DisplayName("happy path saves order, reserves stock, processes payment, publishes event in order")
        void happyPath_savesReservesPaysAndPublishes_inOrder() {
            final ProductEntity product = ProductEntityBuilder.aValidProductBuilder().price(new BigDecimal("50.00")).build();
            final UserEntity user = userWithCart(product, 2, new BigDecimal("50.00"));
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));

            final OrderEntity savedOrder = OrderEntityBuilder.aValidOrderBuilder().userEntity(user).build();
            when(orderRepository.save(any(OrderEntity.class))).thenReturn(savedOrder);

            final PaymentEntity attempt = paymentWith(PaymentAttemptStatus.SUCCESS, TransactionType.PAYMENT,
                    PaymentType.VISA, new Money(new BigDecimal("100.00"), CurrencyCode.EUR), LocalDateTime.now());
            when(paymentService.processPayment(any(), any(), any(), anyString())).thenReturn(attempt);

            final OrderResponseDto resp = service.placeOrder(OrderDtoFixtures.aValidPlaceOrderRequest(), jwt);

            assertThat(resp).isNotNull();

            final InOrder ord = inOrder(orderValidator, orderRepository, stockService, paymentService, eventPublisher);
            ord.verify(orderValidator).validate(any(OrderValidationDto.class));
            ord.verify(orderRepository).save(any(OrderEntity.class));
            ord.verify(stockService).reserveStock(any(UUID.class), any());
            ord.verify(paymentService).processPayment(any(), any(), any(), anyString());
            ord.verify(eventPublisher).publishEvent(any(OrderCreatedEvent.class));
        }

        @Test
        @DisplayName("user missing in repo throws UserNotFoundException")
        void userMissing_throwsUserNotFound() {
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.placeOrder(OrderDtoFixtures.aValidPlaceOrderRequest(), jwt))
                    .isInstanceOf(UserNotFoundException.class);

            verifyNoInteractions(orderRepository, stockService, paymentService, eventPublisher);
        }

        @Test
        @DisplayName("user with null cart throws CartNotFoundException")
        void cartMissing_throwsCartNotFound() {
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(userNoCart()));

            assertThatThrownBy(() -> service.placeOrder(OrderDtoFixtures.aValidPlaceOrderRequest(), jwt))
                    .isInstanceOf(CartNotFoundException.class);

            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("user with empty cart throws CartNotFoundException")
        void cartEmpty_throwsCartNotFound() {
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(userWithEmptyCart()));

            assertThatThrownBy(() -> service.placeOrder(OrderDtoFixtures.aValidPlaceOrderRequest(), jwt))
                    .isInstanceOf(CartNotFoundException.class);

            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("user without default bank card throws NoDefaultBankCartSetException before save")
        void noDefaultCard_throwsNoDefaultBankCart() {
            final ProductEntity product = ProductEntityBuilder.aValidProduct();
            final UserEntity user = userWithCart(product, 1, new BigDecimal("10.00"));
            user.setBankCardEntity(null);
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> service.placeOrder(OrderDtoFixtures.aValidPlaceOrderRequest(), jwt))
                    .isInstanceOf(NoDefaultBankCartSetException.class);

            verify(orderRepository, never()).save(any());
            verifyNoInteractions(orderValidator, paymentService);
        }

        @Test
        @DisplayName("validator rejects -> exception bubbles, no save / no payment")
        void validatorRejects_propagates() {
            final ProductEntity product = ProductEntityBuilder.aValidProduct();
            final UserEntity user = userWithCart(product, 1, new BigDecimal("10.00"));
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));

            doThrowOn(orderValidator);

            assertThatThrownBy(() -> service.placeOrder(OrderDtoFixtures.aValidPlaceOrderRequest(), jwt))
                    .isInstanceOf(IllegalStateException.class);

            verify(orderRepository, never()).save(any());
            verifyNoInteractions(paymentService, eventPublisher);
        }

        private void doThrowOn(final OrderValidator v) {
            org.mockito.Mockito.doThrow(new IllegalStateException("validator-fail"))
                    .when(v).validate(any(OrderValidationDto.class));
        }

        @Test
        @DisplayName("stock reservation fails -> NotEnoughStockException bubbles, no payment, no event")
        void stockReservationFails_propagates() {
            final ProductEntity product = ProductEntityBuilder.aValidProduct();
            final UserEntity user = userWithCart(product, 1, new BigDecimal("10.00"));
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));
            when(orderRepository.save(any(OrderEntity.class))).thenAnswer(inv -> inv.getArgument(0));
            org.mockito.Mockito.doThrow(new NotEnoughStockException("nope"))
                    .when(stockService).reserveStock(any(UUID.class), any());

            assertThatThrownBy(() -> service.placeOrder(OrderDtoFixtures.aValidPlaceOrderRequest(), jwt))
                    .isInstanceOf(NotEnoughStockException.class);

            verifyNoInteractions(paymentService);
            verify(eventPublisher, never()).publishEvent(any());
        }

        /**
         * BUG-050 fix verification (Wave F2).
         * <p>
         * F2 reports: {@code OrderManagementServiceImp.placeOrder} now calls
         * {@code stockService.releaseStock(orderUuid)} when {@code paymentService.processPayment}
         * returns a {@link PaymentEntity} with {@link PaymentAttemptStatus#FAILED}. This test
         * asserts that compensating action — if the fix is in place, the test PASSES; if reverted,
         * it FAILS loudly and we reopen BUG-050.
         */
        @Test
        @DisplayName("BUG-050 fix: payment FAILED path releases stock reservation")
        void paymentFailure_releasesStockReservation() {
            final ProductEntity product = ProductEntityBuilder.aValidProduct();
            final UserEntity user = userWithCart(product, 1, new BigDecimal("10.00"));
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));

            final ArgumentCaptor<OrderEntity> savedOrderCap = ArgumentCaptor.forClass(OrderEntity.class);
            when(orderRepository.save(savedOrderCap.capture())).thenAnswer(inv -> inv.getArgument(0));

            final PaymentEntity failed = paymentWith(PaymentAttemptStatus.FAILED, TransactionType.PAYMENT,
                    PaymentType.VISA, new Money(new BigDecimal("10.00"), CurrencyCode.EUR), LocalDateTime.now());
            when(paymentService.processPayment(any(), any(), any(), anyString())).thenReturn(failed);

            service.placeOrder(OrderDtoFixtures.aValidPlaceOrderRequest(), jwt);

            final UUID orderUuid = savedOrderCap.getValue().getUuid();
            // exactly one releaseStock call with the order UUID — only the failure-compensation path.
            verify(stockService).releaseStock(eq(orderUuid));
            // Event still published with FAILED status (creation-event always fires).
            final ArgumentCaptor<OrderCreatedEvent> evtCap = ArgumentCaptor.forClass(OrderCreatedEvent.class);
            verify(eventPublisher).publishEvent(evtCap.capture());
            assertThat(evtCap.getValue().getOrderEventDto().getPaymentAttemptStatus())
                    .isEqualTo(PaymentAttemptStatus.FAILED);
        }

        @Test
        @DisplayName("event is published AFTER orderRepository.save (InOrder)")
        void eventAfterSave_orderingHolds() {
            final ProductEntity product = ProductEntityBuilder.aValidProduct();
            final UserEntity user = userWithCart(product, 1, new BigDecimal("10.00"));
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));
            when(orderRepository.save(any(OrderEntity.class))).thenAnswer(inv -> inv.getArgument(0));
            when(paymentService.processPayment(any(), any(), any(), anyString())).thenReturn(
                    paymentWith(PaymentAttemptStatus.SUCCESS, TransactionType.PAYMENT, PaymentType.VISA,
                            new Money(new BigDecimal("10.00"), CurrencyCode.EUR), LocalDateTime.now()));

            service.placeOrder(OrderDtoFixtures.aValidPlaceOrderRequest(), jwt);

            final InOrder ord = inOrder(orderRepository, eventPublisher);
            ord.verify(orderRepository).save(any(OrderEntity.class));
            ord.verify(eventPublisher).publishEvent(any(OrderCreatedEvent.class));
        }

        @Test
        @DisplayName("total computed as sum(unitPrice * quantity) and saved as Money.EUR")
        void totalAmountIsSumAndEurByDefault() {
            final ProductEntity product = ProductEntityBuilder.aValidProduct();
            final UserEntity user = userWithCart(product, 3, new BigDecimal("12.50"));
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));

            final ArgumentCaptor<OrderEntity> cap = ArgumentCaptor.forClass(OrderEntity.class);
            when(orderRepository.save(cap.capture())).thenAnswer(inv -> inv.getArgument(0));
            when(paymentService.processPayment(any(), any(), any(), anyString())).thenReturn(
                    paymentWith(PaymentAttemptStatus.SUCCESS, TransactionType.PAYMENT, PaymentType.VISA,
                            new Money(new BigDecimal("37.50"), CurrencyCode.EUR), LocalDateTime.now()));

            service.placeOrder(OrderDtoFixtures.aValidPlaceOrderRequest(), jwt);

            final OrderEntity saved = cap.getValue();
            assertThat(saved.getTotalAmount().getAmount()).isEqualByComparingTo("37.50");
            assertThat(saved.getTotalAmount().getCurrencyCode()).isEqualTo(CurrencyCode.EUR);
        }

        @Test
        @DisplayName("status of the order saved before payment is AWAITING_PAYMENT")
        void savedOrderStatusIsAwaitingPayment() {
            final ProductEntity product = ProductEntityBuilder.aValidProduct();
            final UserEntity user = userWithCart(product, 1, new BigDecimal("10.00"));
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));

            final ArgumentCaptor<OrderEntity> cap = ArgumentCaptor.forClass(OrderEntity.class);
            when(orderRepository.save(cap.capture())).thenAnswer(inv -> inv.getArgument(0));
            when(paymentService.processPayment(any(), any(), any(), anyString())).thenReturn(
                    paymentWith(PaymentAttemptStatus.SUCCESS, TransactionType.PAYMENT, PaymentType.VISA,
                            new Money(new BigDecimal("10.00"), CurrencyCode.EUR), LocalDateTime.now()));

            service.placeOrder(OrderDtoFixtures.aValidPlaceOrderRequest(), jwt);

            assertThat(cap.getValue().getStatus()).isEqualTo(OrderStatus.AWAITING_PAYMENT);
        }

        @Test
        @Disabled("BUG-054: jwt.getSubject() returning null is not guarded — pin desired contract")
        @DisplayName("BUG-054: null JWT subject should throw UserNotFoundException without NPE")
        void placeOrder_nullJwtSubject_shouldThrowUserNotFound() {
            when(jwt.getSubject()).thenReturn(null);
            // Desired: defensive null-guard producing UserNotFoundException without ever calling repo with null.
            assertThatThrownBy(() -> service.placeOrder(OrderDtoFixtures.aValidPlaceOrderRequest(), jwt))
                    .isInstanceOf(UserNotFoundException.class);
            verify(userRepository, never()).findByKeycloakId(null);
        }

        @Test
        @DisplayName("currency: total uses EUR by default — no cross-currency Money.add path")
        void totalAmount_usesEurByDefault_documented() {
            final ProductEntity product = ProductEntityBuilder.aValidProduct();
            final UserEntity user = userWithCart(product, 1, new BigDecimal("10.00"));
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));

            final ArgumentCaptor<OrderEntity> cap = ArgumentCaptor.forClass(OrderEntity.class);
            when(orderRepository.save(cap.capture())).thenAnswer(inv -> inv.getArgument(0));
            when(paymentService.processPayment(any(), any(), any(), anyString())).thenReturn(
                    paymentWith(PaymentAttemptStatus.SUCCESS, TransactionType.PAYMENT, PaymentType.VISA,
                            new Money(new BigDecimal("10.00"), CurrencyCode.EUR), LocalDateTime.now()));

            service.placeOrder(OrderDtoFixtures.aValidPlaceOrderRequest(), jwt);

            assertThat(cap.getValue().getTotalAmount().getCurrencyCode()).isEqualTo(CurrencyCode.EUR);
        }
    }

    // =================================================================
    @Nested
    @DisplayName("cancelOrder")
    class CancelOrder {

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

            service.cancelOrder(uuid, jwt);

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

            assertThatThrownBy(() -> service.cancelOrder(order.getUuid(), jwt))
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

            assertThatThrownBy(() -> service.cancelOrder(order.getUuid(), jwt))
                    .isInstanceOf(CannotCancelOrderException.class);
        }

        @Test
        @DisplayName("order not found throws OrderNotFoundException")
        void notFound_throws() {
            final UUID missing = UUID.randomUUID();
            when(orderRepository.findByUuid(missing)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.cancelOrder(missing, jwt))
                    .isInstanceOf(OrderNotFoundException.class);
        }

        @Test
        @DisplayName("wrong user throws OrderDoesntBelongsToUserException")
        void wrongUser_throws() {
            final UserEntity owner = UserEntityBuilder.aValidUserBuilder().keycloakId("someone-else").build();
            final OrderEntity order = OrderEntityBuilder.aValidOrderBuilder()
                    .userEntity(owner).status(OrderStatus.PAID).build();
            when(orderRepository.findByUuid(order.getUuid())).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> service.cancelOrder(order.getUuid(), jwt))
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

            service.cancelOrder(order.getUuid(), jwt);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
            verifyNoInteractions(paymentService);
        }
    }

    // =================================================================
    @Nested
    @DisplayName("updateOrder")
    class UpdateOrder {

        private OrderEntity prepareOrder(final OrderStatus status, final BigDecimal totalAmount,
                                          final List<PaymentEntity> attempts) {
            final UserEntity user = UserEntityBuilder.aValidUserBuilder()
                    .keycloakId(keycloakId)
                    .bankCardEntity(BankCardEntityBuilder.aValidBankCard())
                    .build();
            return OrderEntityBuilder.aValidOrderBuilder()
                    .userEntity(user)
                    .status(status)
                    .totalAmount(new Money(totalAmount, CurrencyCode.EUR))
                    .orderItemEntities(new ArrayList<>())
                    .paymentAttempts(attempts == null ? new ArrayList<>() : new ArrayList<>(attempts))
                    .build();
        }

        private OrderUpdateRequestDto requestFor(final UUID orderUuid, final ProductEntity product, final int qty) {
            final OrderItemCreateRequestDto item = OrderItemCreateRequestDto.builder()
                    .productUuid(product.getUuid())
                    .quantity(qty)
                    .build();
            return OrderDtoFixtures.aValidUpdateRequestBuilder()
                    .uuid(orderUuid)
                    .itemUpdateRequestDtoList(List.of(item))
                    .build();
        }

        @Test
        @DisplayName("zero-delta: total unchanged -> commitStock called and status forced to PAID")
        void zeroDelta_commitsStockAndForcesPaid() {
            final ProductEntity product = ProductEntityBuilder.aValidProductBuilder().price(new BigDecimal("100.00")).build();
            final OrderEntity order = prepareOrder(OrderStatus.AWAITING_PAYMENT, new BigDecimal("100.00"),
                    List.of(paymentWith(PaymentAttemptStatus.SUCCESS, TransactionType.PAYMENT, PaymentType.VISA,
                            new Money(new BigDecimal("100.00"), CurrencyCode.EUR), LocalDateTime.now())));
            when(orderRepository.findByUuid(order.getUuid())).thenReturn(Optional.of(order));
            when(productRepository.findAllByUuidIn(any())).thenReturn(List.of(product));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            final OrderUpdateRequestDto req = requestFor(order.getUuid(), product, 1);
            service.updateOrder(req, jwt);

            verify(stockService).commitStock(order.getUuid());
            verify(stockService).releaseStock(order.getUuid()); // pre-clear before re-reserve
            verify(stockService).reserveStock(eq(order.getUuid()), any());
            verify(paymentService, never()).processPayment(any(), any(), any(), anyString());
            verify(paymentService, never()).refund(any(), any(), any(), anyString());
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        }

        @Test
        @DisplayName("positive-delta: amount higher -> processPayment for the difference, status AWAITING_PAYMENT")
        void positiveDelta_processesPayment() {
            final ProductEntity product = ProductEntityBuilder.aValidProductBuilder().price(new BigDecimal("150.00")).build();
            final OrderEntity order = prepareOrder(OrderStatus.PAID, new BigDecimal("100.00"),
                    List.of(paymentWith(PaymentAttemptStatus.SUCCESS, TransactionType.PAYMENT, PaymentType.VISA,
                            new Money(new BigDecimal("100.00"), CurrencyCode.EUR), LocalDateTime.now())));
            when(orderRepository.findByUuid(order.getUuid())).thenReturn(Optional.of(order));
            when(productRepository.findAllByUuidIn(any())).thenReturn(List.of(product));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(paymentService.processPayment(any(), any(), any(), anyString()))
                    .thenReturn(paymentWith(PaymentAttemptStatus.SUCCESS, TransactionType.PAYMENT, PaymentType.VISA,
                            new Money(new BigDecimal("50.00"), CurrencyCode.EUR), LocalDateTime.now()));

            service.updateOrder(requestFor(order.getUuid(), product, 1), jwt);

            final ArgumentCaptor<Money> moneyCap = ArgumentCaptor.forClass(Money.class);
            verify(paymentService).processPayment(eq(order), eq(PaymentType.VISA), moneyCap.capture(), anyString());
            assertThat(moneyCap.getValue().getAmount()).isEqualByComparingTo("50.00");
            assertThat(order.getStatus()).isEqualTo(OrderStatus.AWAITING_PAYMENT);
            verify(stockService, never()).commitStock(any());
        }

        @Test
        @DisplayName("negative-delta: amount lower -> refund for the absolute difference")
        void negativeDelta_refunds() {
            final ProductEntity product = ProductEntityBuilder.aValidProductBuilder().price(new BigDecimal("40.00")).build();
            final OrderEntity order = prepareOrder(OrderStatus.PAID, new BigDecimal("100.00"),
                    List.of(paymentWith(PaymentAttemptStatus.SUCCESS, TransactionType.PAYMENT, PaymentType.VISA,
                            new Money(new BigDecimal("100.00"), CurrencyCode.EUR), LocalDateTime.now())));
            when(orderRepository.findByUuid(order.getUuid())).thenReturn(Optional.of(order));
            when(productRepository.findAllByUuidIn(any())).thenReturn(List.of(product));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(paymentService.refund(any(), any(), any(), anyString()))
                    .thenReturn(paymentWith(PaymentAttemptStatus.SUCCESS, TransactionType.REFUND, PaymentType.VISA,
                            new Money(new BigDecimal("60.00"), CurrencyCode.EUR), LocalDateTime.now()));

            service.updateOrder(requestFor(order.getUuid(), product, 1), jwt);

            final ArgumentCaptor<Money> moneyCap = ArgumentCaptor.forClass(Money.class);
            verify(paymentService).refund(eq(order), eq(PaymentType.VISA), moneyCap.capture(), anyString());
            assertThat(moneyCap.getValue().getAmount()).isEqualByComparingTo("60.00"); // abs diff
            verify(paymentService, never()).processPayment(any(), any(), any(), anyString());
        }

        @Test
        @DisplayName("not found throws OrderNotFoundException")
        void notFound_throws() {
            final UUID missing = UUID.randomUUID();
            when(orderRepository.findByUuid(missing)).thenReturn(Optional.empty());
            final OrderUpdateRequestDto req = OrderDtoFixtures.aValidUpdateRequestBuilder().uuid(missing).build();

            assertThatThrownBy(() -> service.updateOrder(req, jwt))
                    .isInstanceOf(OrderNotFoundException.class);
        }

        @Test
        @DisplayName("wrong user throws OrderDoesntBelongsToUserException")
        void wrongUser_throws() {
            final UserEntity foreigner = UserEntityBuilder.aValidUserBuilder().keycloakId("foreign").build();
            final OrderEntity order = OrderEntityBuilder.aValidOrderBuilder().userEntity(foreigner).build();
            when(orderRepository.findByUuid(order.getUuid())).thenReturn(Optional.of(order));

            assertThatThrownBy(() ->
                    service.updateOrder(OrderDtoFixtures.aValidUpdateRequestBuilder().uuid(order.getUuid()).build(), jwt))
                    .isInstanceOf(OrderDoesntBelongsToUserException.class);

            verifyNoInteractions(stockService, paymentService);
        }

        @Test
        @DisplayName("already-shipped throws OrderAlreadyShippedException without releasing stock")
        void alreadyShipped_throws() {
            final UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).build();
            final OrderEntity order = OrderEntityBuilder.aValidOrderBuilder()
                    .userEntity(user).status(OrderStatus.SHIPPED).build();
            when(orderRepository.findByUuid(order.getUuid())).thenReturn(Optional.of(order));

            assertThatThrownBy(() ->
                    service.updateOrder(OrderDtoFixtures.aValidUpdateRequestBuilder().uuid(order.getUuid()).build(), jwt))
                    .isInstanceOf(OrderAlreadyShippedException.class);

            verifyNoInteractions(stockService, paymentService);
        }

        @Test
        @DisplayName("paidAmount nets out refunds: prior PAYMENT 100 + REFUND 30 -> paid=70")
        void paidAmountNetsOutRefunds() {
            final ProductEntity product = ProductEntityBuilder.aValidProductBuilder().price(new BigDecimal("70.00")).build();
            // 70 (new total) - 70 (paid: 100 - 30) = 0 difference -> commit branch
            final List<PaymentEntity> attempts = List.of(
                    paymentWith(PaymentAttemptStatus.SUCCESS, TransactionType.PAYMENT, PaymentType.VISA,
                            new Money(new BigDecimal("100.00"), CurrencyCode.EUR), LocalDateTime.now()),
                    paymentWith(PaymentAttemptStatus.SUCCESS, TransactionType.REFUND, PaymentType.VISA,
                            new Money(new BigDecimal("30.00"), CurrencyCode.EUR), LocalDateTime.now()));
            final OrderEntity order = prepareOrder(OrderStatus.PAID, new BigDecimal("70.00"), attempts);
            when(orderRepository.findByUuid(order.getUuid())).thenReturn(Optional.of(order));
            when(productRepository.findAllByUuidIn(any())).thenReturn(List.of(product));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.updateOrder(requestFor(order.getUuid(), product, 1), jwt);

            verify(stockService).commitStock(order.getUuid());
            verify(paymentService, never()).processPayment(any(), any(), any(), anyString());
            verify(paymentService, never()).refund(any(), any(), any(), anyString());
        }

        @Test
        @DisplayName("product missing from fetched products: silently treated as 0 in total computation")
        void productMissingFromFetch_silentlySkipped() {
            // Request asks for one product, fetched list is empty -> processOrderTotalPrice returns 0
            // Prior paid = 0 -> difference 0 -> commit branch and status forced to PAID.
            final UUID requestedProductUuid = UUID.randomUUID();
            final UserEntity user = UserEntityBuilder.aValidUserBuilder()
                    .keycloakId(keycloakId)
                    .bankCardEntity(BankCardEntityBuilder.aValidBankCard())
                    .build();
            final OrderEntity order = OrderEntityBuilder.aValidOrderBuilder()
                    .userEntity(user)
                    .status(OrderStatus.AWAITING_PAYMENT)
                    .totalAmount(new Money(BigDecimal.ZERO, CurrencyCode.EUR))
                    .paymentAttempts(new ArrayList<>())
                    .build();
            when(orderRepository.findByUuid(order.getUuid())).thenReturn(Optional.of(order));
            when(productRepository.findAllByUuidIn(any())).thenReturn(List.of()); // empty!
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            final OrderItemCreateRequestDto item = OrderItemCreateRequestDto.builder()
                    .productUuid(requestedProductUuid).quantity(2).build();
            final OrderUpdateRequestDto req = OrderDtoFixtures.aValidUpdateRequestBuilder()
                    .uuid(order.getUuid()).itemUpdateRequestDtoList(List.of(item)).build();

            service.updateOrder(req, jwt);

            verify(stockService).commitStock(order.getUuid());
            verify(paymentService, never()).processPayment(any(), any(), any(), anyString());
            verify(paymentService, never()).refund(any(), any(), any(), anyString());
        }

        @Test
        @DisplayName("address, total and shipping fields are updated on the order before save")
        void updatesAddressTotalAndStatus() {
            final ProductEntity product = ProductEntityBuilder.aValidProductBuilder().price(new BigDecimal("99.00")).build();
            final OrderEntity order = prepareOrder(OrderStatus.AWAITING_PAYMENT, new BigDecimal("99.00"),
                    List.of(paymentWith(PaymentAttemptStatus.SUCCESS, TransactionType.PAYMENT, PaymentType.VISA,
                            new Money(new BigDecimal("99.00"), CurrencyCode.EUR), LocalDateTime.now())));
            when(orderRepository.findByUuid(order.getUuid())).thenReturn(Optional.of(order));
            when(productRepository.findAllByUuidIn(any())).thenReturn(List.of(product));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            final OrderUpdateRequestDto req = OrderDtoFixtures.aValidUpdateRequestBuilder()
                    .uuid(order.getUuid())
                    .shippingStreet("9 avenue Test")
                    .shippingCity("Lyon")
                    .shippingZipCode("69000")
                    .shippingCountry("FR")
                    .shippingType(ShippingType.EXPRESS)
                    .shippingProvider(ShippingProvider.FEDEX)
                    .itemUpdateRequestDtoList(List.of(OrderItemCreateRequestDto.builder()
                            .productUuid(product.getUuid()).quantity(1).build()))
                    .build();

            service.updateOrder(req, jwt);

            assertThat(order.getShippingAddress().getStreet()).isEqualTo("9 avenue Test");
            assertThat(order.getShippingAddress().getCity()).isEqualTo("Lyon");
            assertThat(order.getShippingType()).isEqualTo(ShippingType.EXPRESS);
            assertThat(order.getShippingProvider()).isEqualTo(ShippingProvider.FEDEX);
            assertThat(order.getTotalAmount().getAmount()).isEqualByComparingTo("99.00");
        }
    }

    // =================================================================
    @Nested
    @DisplayName("retryPayment")
    class RetryPayment {

        private OrderEntity orderWithLastAttempt(final OrderStatus status,
                                                  final PaymentType lastType,
                                                  final BigDecimal totalAmount) {
            final UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).build();
            final ProductEntity product = ProductEntityBuilder.aValidProduct();
            final OrderItemEntity orderItem = OrderItemEntityBuilder.aValidOrderItemBuilder()
                    .productEntity(product).quantity(1).build();

            final List<PaymentEntity> attempts = new ArrayList<>(List.of(
                    paymentWith(PaymentAttemptStatus.FAILED, TransactionType.PAYMENT, PaymentType.MASTERCARD,
                            new Money(totalAmount, CurrencyCode.EUR), LocalDateTime.now().minusMinutes(5)),
                    paymentWith(PaymentAttemptStatus.FAILED, TransactionType.PAYMENT, lastType,
                            new Money(totalAmount, CurrencyCode.EUR), LocalDateTime.now())));

            return OrderEntityBuilder.aValidOrderBuilder()
                    .userEntity(user)
                    .status(status)
                    .totalAmount(new Money(totalAmount, CurrencyCode.EUR))
                    .orderItemEntities(new ArrayList<>(List.of(orderItem)))
                    .paymentAttempts(attempts)
                    .build();
        }

        @Test
        @DisplayName("happy path: re-uses last attempt's PaymentType and forwards order.totalAmount verbatim")
        void happy_reusesLastAttemptType() {
            final OrderEntity order = orderWithLastAttempt(OrderStatus.PAYMENT_FAILED, PaymentType.VISA, new BigDecimal("100.00"));
            when(orderRepository.findByUuid(order.getUuid())).thenReturn(Optional.of(order));
            when(paymentService.processPayment(any(), any(), any(), anyString())).thenReturn(
                    paymentWith(PaymentAttemptStatus.SUCCESS, TransactionType.PAYMENT, PaymentType.VISA,
                            new Money(new BigDecimal("100.00"), CurrencyCode.EUR), LocalDateTime.now()));

            service.retryPayment(order.getUuid(), jwt);

            verify(stockService).reserveStock(eq(order.getUuid()), any());
            verify(paymentService).processPayment(eq(order), eq(PaymentType.VISA), eq(order.getTotalAmount()), anyString());
            verify(eventPublisher).publishEvent(any(OrderUpdatedEvent.class));
        }

        @Test
        @DisplayName("PAID order is NOT retryable -> FailedRetryingPayment")
        void paidStatus_throwsFailedRetrying() {
            final OrderEntity order = orderWithLastAttempt(OrderStatus.PAID, PaymentType.VISA, new BigDecimal("100.00"));
            when(orderRepository.findByUuid(order.getUuid())).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> service.retryPayment(order.getUuid(), jwt))
                    .isInstanceOf(FailedRetryingPayment.class);

            verifyNoInteractions(paymentService, stockService);
        }

        @Test
        @DisplayName("CREATED order IS retryable (per service's permissive set)")
        void createdStatus_retries() {
            final OrderEntity order = orderWithLastAttempt(OrderStatus.CREATED, PaymentType.VISA, new BigDecimal("100.00"));
            when(orderRepository.findByUuid(order.getUuid())).thenReturn(Optional.of(order));
            when(paymentService.processPayment(any(), any(), any(), anyString())).thenReturn(
                    paymentWith(PaymentAttemptStatus.SUCCESS, TransactionType.PAYMENT, PaymentType.VISA,
                            new Money(new BigDecimal("100.00"), CurrencyCode.EUR), LocalDateTime.now()));

            service.retryPayment(order.getUuid(), jwt);

            verify(paymentService).processPayment(any(), eq(PaymentType.VISA), any(), anyString());
        }

        @Test
        @DisplayName("not found throws OrderNotFoundException")
        void notFound_throws() {
            final UUID missing = UUID.randomUUID();
            when(orderRepository.findByUuid(missing)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.retryPayment(missing, jwt))
                    .isInstanceOf(OrderNotFoundException.class);
        }

        @Test
        @DisplayName("wrong user throws OrderDoesntBelongsToUserException")
        void wrongUser_throws() {
            final UserEntity foreigner = UserEntityBuilder.aValidUserBuilder().keycloakId("foreign").build();
            final OrderEntity order = OrderEntityBuilder.aValidOrderBuilder()
                    .userEntity(foreigner).status(OrderStatus.PAYMENT_FAILED).build();
            when(orderRepository.findByUuid(order.getUuid())).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> service.retryPayment(order.getUuid(), jwt))
                    .isInstanceOf(OrderDoesntBelongsToUserException.class);
        }

        @Test
        @DisplayName("no previous payment attempt throws NoPreviousPaymentAttemptException")
        void noPreviousAttempt_throws() {
            final UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).build();
            final ProductEntity product = ProductEntityBuilder.aValidProduct();
            final OrderItemEntity item = OrderItemEntityBuilder.aValidOrderItemBuilder()
                    .productEntity(product).quantity(1).build();
            final OrderEntity order = OrderEntityBuilder.aValidOrderBuilder()
                    .userEntity(user)
                    .status(OrderStatus.PAYMENT_FAILED)
                    .orderItemEntities(new ArrayList<>(List.of(item)))
                    .paymentAttempts(new ArrayList<>())
                    .build();
            when(orderRepository.findByUuid(order.getUuid())).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> service.retryPayment(order.getUuid(), jwt))
                    .isInstanceOf(NoPreviousPaymentAttemptException.class);

            verify(paymentService, never()).processPayment(any(), any(), any(), anyString());
        }

        @Test
        @DisplayName("stock reservation failure surfaces NotEnoughStockException, no payment attempted")
        void stockReservationFails_propagates() {
            final OrderEntity order = orderWithLastAttempt(OrderStatus.PAYMENT_FAILED, PaymentType.VISA, new BigDecimal("100.00"));
            when(orderRepository.findByUuid(order.getUuid())).thenReturn(Optional.of(order));
            org.mockito.Mockito.doThrow(new NotEnoughStockException("nope"))
                    .when(stockService).reserveStock(any(UUID.class), any());

            assertThatThrownBy(() -> service.retryPayment(order.getUuid(), jwt))
                    .isInstanceOf(NotEnoughStockException.class);

            verifyNoInteractions(paymentService);
        }

        @Test
        @Disabled("BUG-052: retryPayment forwards order.totalAmount verbatim — discount may be re-applied (double-discount). Pending product decision.")
        @DisplayName("BUG-052: retryPayment should not double-apply discount on already-discounted total")
        void retryPayment_shouldNotDoubleApplyDiscount() {
            // Desired: when order.discountType != NO_DISCOUNT, retryPayment should NOT re-apply the discount
            // on top of order.totalAmount (which already reflects the first discount). Pinned for product
            // decision — no enforcement in current code.
            final OrderEntity order = orderWithLastAttempt(OrderStatus.PAYMENT_FAILED, PaymentType.VISA, new BigDecimal("60.00"));
            order.setDiscountType(DiscountType.BLACK_FRIDAY);
            when(orderRepository.findByUuid(order.getUuid())).thenReturn(Optional.of(order));
            when(paymentService.processPayment(any(), any(), any(), anyString())).thenReturn(
                    paymentWith(PaymentAttemptStatus.SUCCESS, TransactionType.PAYMENT, PaymentType.VISA,
                            new Money(new BigDecimal("60.00"), CurrencyCode.EUR), LocalDateTime.now()));

            service.retryPayment(order.getUuid(), jwt);

            // Currently the service forwards 60 (already-discounted) — desired: should not re-discount to 36.
            // This assertion would FAIL on a buggy implementation that re-applies the strategy.
            verify(paymentService).processPayment(any(), any(), eq(order.getTotalAmount()), anyString());
        }
    }

    // =================================================================
    @Nested
    @DisplayName("getAll / getByUUID / getByUUIDs (admin reads)")
    class Reads {

        @Test
        @DisplayName("getAll returns all orders mapped to response DTOs")
        void getAll_happy() {
            final OrderEntity o1 = OrderEntityBuilder.aValidOrder();
            final OrderEntity o2 = OrderEntityBuilder.aValidOrder();
            when(orderRepository.findAll()).thenReturn(List.of(o1, o2));

            final Collection<OrderResponseDto> all = service.getAll();
            assertThat(all).hasSize(2);
            verify(orderMapper, times(2)).mapFromEntityToResponseDto(any(OrderEntity.class));
        }

        @Test
        @DisplayName("getByUUID happy path")
        void getByUUID_happy() {
            final OrderEntity o = OrderEntityBuilder.aValidOrder();
            when(orderRepository.findByUuid(o.getUuid())).thenReturn(Optional.of(o));

            final OrderResponseDto resp = service.getByUUID(o.getUuid());

            assertThat(resp).isNotNull();
            verify(orderMapper).mapFromEntityToResponseDto(o);
        }

        @Test
        @DisplayName("getByUUID throws when not found")
        void getByUUID_notFound() {
            final UUID missing = UUID.randomUUID();
            when(orderRepository.findByUuid(missing)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getByUUID(missing))
                    .isInstanceOf(OrderNotFoundException.class);
        }

        @Test
        @DisplayName("getByUUIDs returns mapped collection")
        void getByUUIDs_happy() {
            final OrderEntity o = OrderEntityBuilder.aValidOrder();
            when(orderRepository.findAllByUuidIn(any())).thenReturn(List.of(o));

            final Collection<OrderResponseDto> resp = service.getByUUIDs(List.of(o.getUuid()));

            assertThat(resp).hasSize(1);
        }
    }

    // =================================================================
    @Nested
    @DisplayName("deleteByUUID")
    class DeleteByUuid {

        private OrderEntity ownedOrderInStatus(final OrderStatus s) {
            final UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).build();
            return OrderEntityBuilder.aValidOrderBuilder().userEntity(user).status(s).build();
        }

        @Test
        @DisplayName("happy: releases stock and deletes by uuid")
        void happy() {
            final OrderEntity o = ownedOrderInStatus(OrderStatus.CREATED);
            when(orderRepository.findByUuid(o.getUuid())).thenReturn(Optional.of(o));

            service.deleteByUUID(o.getUuid(), jwt);

            final InOrder ord = inOrder(stockService, orderRepository);
            ord.verify(stockService).releaseStock(o.getUuid());
            ord.verify(orderRepository).deleteByUuid(o.getUuid());
        }

        @Test
        @DisplayName("not found throws OrderNotFoundException")
        void notFound() {
            final UUID missing = UUID.randomUUID();
            when(orderRepository.findByUuid(missing)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.deleteByUUID(missing, jwt))
                    .isInstanceOf(OrderNotFoundException.class);
        }

        @Test
        @DisplayName("wrong user throws OrderDoesntBelongsToUserException")
        void wrongUser() {
            final UserEntity foreigner = UserEntityBuilder.aValidUserBuilder().keycloakId("other").build();
            final OrderEntity o = OrderEntityBuilder.aValidOrderBuilder().userEntity(foreigner)
                    .status(OrderStatus.CREATED).build();
            when(orderRepository.findByUuid(o.getUuid())).thenReturn(Optional.of(o));

            assertThatThrownBy(() -> service.deleteByUUID(o.getUuid(), jwt))
                    .isInstanceOf(OrderDoesntBelongsToUserException.class);

            verifyNoInteractions(stockService);
        }

        @Test
        @DisplayName("SHIPPED is NOT in deletable state -> CannotCancelOrderException")
        void shipped_notDeletable() {
            final OrderEntity o = ownedOrderInStatus(OrderStatus.SHIPPED);
            when(orderRepository.findByUuid(o.getUuid())).thenReturn(Optional.of(o));

            assertThatThrownBy(() -> service.deleteByUUID(o.getUuid(), jwt))
                    .isInstanceOf(CannotCancelOrderException.class);
        }

        @Test
        @DisplayName("PAID is NOT in deletable state")
        void paid_notDeletable() {
            final OrderEntity o = ownedOrderInStatus(OrderStatus.PAID);
            when(orderRepository.findByUuid(o.getUuid())).thenReturn(Optional.of(o));

            assertThatThrownBy(() -> service.deleteByUUID(o.getUuid(), jwt))
                    .isInstanceOf(CannotCancelOrderException.class);
        }

        @Test
        @DisplayName("DELIVERED IS in the deletable set (documents the design oddity)")
        void delivered_isDeletable() {
            final OrderEntity o = ownedOrderInStatus(OrderStatus.DELIVERED);
            when(orderRepository.findByUuid(o.getUuid())).thenReturn(Optional.of(o));

            service.deleteByUUID(o.getUuid(), jwt);

            verify(orderRepository).deleteByUuid(o.getUuid());
        }

        @Test
        @DisplayName("PAYMENT_FAILED IS in the deletable set")
        void paymentFailed_isDeletable() {
            final OrderEntity o = ownedOrderInStatus(OrderStatus.PAYMENT_FAILED);
            when(orderRepository.findByUuid(o.getUuid())).thenReturn(Optional.of(o));

            service.deleteByUUID(o.getUuid(), jwt);

            verify(orderRepository).deleteByUuid(o.getUuid());
        }
    }

    // =================================================================
    @Nested
    @DisplayName("Cross-cutting (idempotency keys, response mapping)")
    class CrossCutting {

        @Test
        @DisplayName("placeOrder uses the (String, List<String>) idempotency overload with productUuids")
        void placeOrder_usesListOverload() {
            final ProductEntity product = ProductEntityBuilder.aValidProduct();
            final UserEntity user = userWithCart(product, 1, new BigDecimal("10.00"));
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(paymentService.processPayment(any(), any(), any(), anyString())).thenReturn(
                    paymentWith(PaymentAttemptStatus.SUCCESS, TransactionType.PAYMENT, PaymentType.VISA,
                            new Money(new BigDecimal("10.00"), CurrencyCode.EUR), LocalDateTime.now()));

            service.placeOrder(OrderDtoFixtures.aValidPlaceOrderRequest(), jwt);

            // The (String, List<String>) overload must be hit for placeOrder.
            verify(idempotencyKeyService).generateKey(anyString(), anyList());
        }

        @Test
        @DisplayName("retryPayment uses the default (String, String) overload with literal 'retry'")
        void retryPayment_usesStringOverload() {
            final UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).build();
            final ProductEntity product = ProductEntityBuilder.aValidProduct();
            final OrderItemEntity item = OrderItemEntityBuilder.aValidOrderItemBuilder()
                    .productEntity(product).quantity(1).build();
            final OrderEntity order = OrderEntityBuilder.aValidOrderBuilder()
                    .userEntity(user)
                    .status(OrderStatus.PAYMENT_FAILED)
                    .totalAmount(new Money(new BigDecimal("10.00"), CurrencyCode.EUR))
                    .orderItemEntities(new ArrayList<>(List.of(item)))
                    .paymentAttempts(new ArrayList<>(List.of(paymentWith(PaymentAttemptStatus.FAILED, TransactionType.PAYMENT,
                            PaymentType.VISA, new Money(new BigDecimal("10.00"), CurrencyCode.EUR), LocalDateTime.now()))))
                    .build();
            when(orderRepository.findByUuid(order.getUuid())).thenReturn(Optional.of(order));
            when(paymentService.processPayment(any(), any(), any(), anyString())).thenReturn(
                    paymentWith(PaymentAttemptStatus.SUCCESS, TransactionType.PAYMENT, PaymentType.VISA,
                            new Money(new BigDecimal("10.00"), CurrencyCode.EUR), LocalDateTime.now()));

            service.retryPayment(order.getUuid(), jwt);

            // The default-method (String, String) overload delegates to the (String, List<String>) one
            // — assert the (String, String) entry-point was hit with the literal "retry".
            verify(idempotencyKeyService).generateKey(eq(order.getUuid().toString()), eq("retry"));
        }
    }
}
