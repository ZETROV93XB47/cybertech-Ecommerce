package com.novatech.cybertech.services.implementation.order;

import com.novatech.cybertech.dto.data.OrderEventDto;
import com.novatech.cybertech.dto.data.OrderValidationDto;
import com.novatech.cybertech.dto.request.order.OrderPlacingRequestDto;
import com.novatech.cybertech.dto.request.order.OrderUpdateRequestDto;
import com.novatech.cybertech.dto.request.order.PriceCalculationRequestDto;
import com.novatech.cybertech.dto.request.orderItem.OrderItemCreateRequestDto;
import com.novatech.cybertech.dto.response.order.OrderResponseDto;
import com.novatech.cybertech.dto.response.order.PriceCalculationResultDto;
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
import com.novatech.cybertech.services.core.OrderPriceCalculationService;
import com.novatech.cybertech.services.core.PaymentService;
import com.novatech.cybertech.services.core.StockService;
import com.novatech.cybertech.services.implementation.OrderManagementServiceImp;
import com.novatech.cybertech.validator.core.OrderValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
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
import static org.mockito.ArgumentMatchers.argThat;
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
    @Mock OrderPriceCalculationService orderPriceCalculationService;

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

        // Default stub for orderPriceCalculationService — tests that need a specific amount override this.
        // Includes a non-null shippingCost reflecting the SHIPPING-INT integration so any consumer that
        // starts reading getShippingCost() will see a deterministic value.
        lenient().when(orderPriceCalculationService.calculate(any(PriceCalculationRequestDto.class)))
                .thenReturn(PriceCalculationResultDto.builder()
                        .baseAmount(new BigDecimal("10.00"))
                        .discountAmount(BigDecimal.ZERO)
                        .shippingCost(new BigDecimal("5.00"))
                        .finalAmount(new BigDecimal("10.00"))
                        .currencyCode(CurrencyCode.EUR)
                        .discountType(DiscountType.NO_DISCOUNT)
                        .build());
    }

    @AfterEach
    void clearSecurityContext() {
        // The admin-bypass tests below mutate the SecurityContextHolder to inject ROLE_ADMIN.
        // Clearing here keeps those mutations from leaking into sibling tests / parallel runs.
        SecurityContextHolder.clearContext();
    }

    /**
     * Helper to install an authentication carrying the requested role into the
     * {@link SecurityContextHolder} — drives {@code OrderManagementServiceImp.isCurrentCallerAdmin()}.
     */
    private static void setAuthenticatedRole(final String role) {
        final TestingAuthenticationToken auth = new TestingAuthenticationToken("test-user", "n/a", role);
        auth.setAuthenticated(true);
        final SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);
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
        @DisplayName("total computed via OrderPriceCalculationService and saved as Money.EUR")
        void totalAmountIsSumAndEurByDefault() {
            final ProductEntity product = ProductEntityBuilder.aValidProduct();
            final UserEntity user = userWithCart(product, 3, new BigDecimal("12.50"));
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));
            // Override default stub: price calculation service returns 37.50 (= 3 × 12.50, no discount)
            when(orderPriceCalculationService.calculate(any(PriceCalculationRequestDto.class)))
                    .thenReturn(PriceCalculationResultDto.builder()
                            .baseAmount(new BigDecimal("37.50"))
                            .discountAmount(BigDecimal.ZERO)
                            .finalAmount(new BigDecimal("37.50"))
                            .currencyCode(CurrencyCode.EUR)
                            .discountType(DiscountType.NO_DISCOUNT)
                            .build());

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
        @DisplayName("BUG-054 FIX: null JWT subject throws UserNotFoundException without NPE and never queries repo")
        void placeOrder_nullJwtSubject_shouldThrowUserNotFound() {
            // BUG-054 FIX verified: resolveKeycloakIdFromJwt surfaces a missing sub claim as
            // UserNotFoundException before any repo call — no NPE, no findByKeycloakId(null).
            when(jwt.getSubject()).thenReturn(null);

            assertThatThrownBy(() -> service.placeOrder(OrderDtoFixtures.aValidPlaceOrderRequest(), jwt))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessageContaining("JWT subject missing");
            verify(userRepository, never()).findByKeycloakId(null);
        }

        /**
         * SHIPPING-INT contract: placeOrder must forward {@code shippingProvider} and
         * {@code shippingType} from the {@link OrderPlacingRequestDto} into the
         * {@link PriceCalculationRequestDto} so the price calculation service can resolve the
         * correct {@link com.novatech.cybertech.services.core.ShippingProviderService} and fold
         * the shipping cost into the final amount.
         */
        @Test
        @DisplayName("SHIPPING-INT: placeOrder forwards shippingProvider + shippingType to OrderPriceCalculationService")
        void placeOrder_forwardsShippingProviderAndTypeToPriceCalc() {
            final ProductEntity product = ProductEntityBuilder.aValidProduct();
            final UserEntity user = userWithCart(product, 1, new BigDecimal("10.00"));
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));
            when(orderRepository.save(any(OrderEntity.class))).thenAnswer(inv -> inv.getArgument(0));
            when(paymentService.processPayment(any(), any(), any(), anyString())).thenReturn(
                    paymentWith(PaymentAttemptStatus.SUCCESS, TransactionType.PAYMENT, PaymentType.VISA,
                            new Money(new BigDecimal("10.00"), CurrencyCode.EUR), LocalDateTime.now()));

            // Use a non-default (FEDEX / EXPRESS) pair so we can prove the values come from the request.
            final OrderPlacingRequestDto req = OrderDtoFixtures.aValidPlaceOrderRequestBuilder()
                    .shippingProvider(ShippingProvider.FEDEX)
                    .shippingType(ShippingType.EXPRESS)
                    .build();

            service.placeOrder(req, jwt);

            final ArgumentCaptor<PriceCalculationRequestDto> cap = ArgumentCaptor.forClass(PriceCalculationRequestDto.class);
            verify(orderPriceCalculationService).calculate(cap.capture());
            assertThat(cap.getValue().getShippingProvider()).isEqualTo(ShippingProvider.FEDEX);
            assertThat(cap.getValue().getShippingType()).isEqualTo(ShippingType.EXPRESS);
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

            assertThatThrownBy(() -> service.cancelOrder(order.getUuid(), jwt))
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

            assertThatThrownBy(() -> service.cancelOrder(order.getUuid(), jwt))
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

            service.cancelOrder(order.getUuid(), jwt);

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
            when(orderPriceCalculationService.calculate(any(PriceCalculationRequestDto.class)))
                    .thenReturn(PriceCalculationResultDto.builder()
                            .baseAmount(new BigDecimal("100.00")).discountAmount(BigDecimal.ZERO)
                            .finalAmount(new BigDecimal("100.00")).currencyCode(CurrencyCode.EUR)
                            .discountType(DiscountType.NO_DISCOUNT).build());

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
            when(orderPriceCalculationService.calculate(any(PriceCalculationRequestDto.class)))
                    .thenReturn(PriceCalculationResultDto.builder()
                            .baseAmount(new BigDecimal("150.00")).discountAmount(BigDecimal.ZERO)
                            .finalAmount(new BigDecimal("150.00")).currencyCode(CurrencyCode.EUR)
                            .discountType(DiscountType.NO_DISCOUNT).build());
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
            when(orderPriceCalculationService.calculate(any(PriceCalculationRequestDto.class)))
                    .thenReturn(PriceCalculationResultDto.builder()
                            .baseAmount(new BigDecimal("40.00")).discountAmount(BigDecimal.ZERO)
                            .finalAmount(new BigDecimal("40.00")).currencyCode(CurrencyCode.EUR)
                            .discountType(DiscountType.NO_DISCOUNT).build());
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
            when(orderPriceCalculationService.calculate(any(PriceCalculationRequestDto.class)))
                    .thenReturn(PriceCalculationResultDto.builder()
                            .baseAmount(new BigDecimal("70.00")).discountAmount(BigDecimal.ZERO)
                            .finalAmount(new BigDecimal("70.00")).currencyCode(CurrencyCode.EUR)
                            .discountType(DiscountType.NO_DISCOUNT).build());

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
            when(orderPriceCalculationService.calculate(any(PriceCalculationRequestDto.class)))
                    .thenReturn(PriceCalculationResultDto.builder()
                            .baseAmount(new BigDecimal("99.00")).discountAmount(BigDecimal.ZERO)
                            .finalAmount(new BigDecimal("99.00")).currencyCode(CurrencyCode.EUR)
                            .discountType(DiscountType.NO_DISCOUNT).build());

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

            // SHIPPING-INT contract: updateOrder must forward shippingProvider/shippingType from the
            // request into the PriceCalculationRequestDto for the price-calculation service.
            final ArgumentCaptor<PriceCalculationRequestDto> priceCap =
                    ArgumentCaptor.forClass(PriceCalculationRequestDto.class);
            verify(orderPriceCalculationService).calculate(priceCap.capture());
            assertThat(priceCap.getValue().getShippingProvider()).isEqualTo(ShippingProvider.FEDEX);
            assertThat(priceCap.getValue().getShippingType()).isEqualTo(ShippingType.EXPRESS);
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
        @DisplayName("BUG-052 FIX: retryPayment forwards stored discount-adjusted total verbatim — no double-discount")
        void retryPayment_shouldNotDoubleApplyDiscount() {
            // BUG-052 contract verified: order.totalAmount is the POST-discount final amount written
            // once at placeOrder time. retryPayment forwards it verbatim to processPayment — never
            // re-applying the discount strategy on top of an already-discounted total. A customer
            // retrying after a transient payment failure pays exactly the same amount as the original
            // attempt, regardless of discountType.
            final OrderEntity order = orderWithLastAttempt(OrderStatus.PAYMENT_FAILED, PaymentType.VISA, new BigDecimal("60.00"));
            order.setDiscountType(DiscountType.BLACK_FRIDAY);
            final Money originalTotal = order.getTotalAmount();
            when(orderRepository.findByUuid(order.getUuid())).thenReturn(Optional.of(order));
            when(paymentService.processPayment(any(), any(), any(), anyString())).thenReturn(
                    paymentWith(PaymentAttemptStatus.SUCCESS, TransactionType.PAYMENT, PaymentType.VISA,
                            new Money(new BigDecimal("60.00"), CurrencyCode.EUR), LocalDateTime.now()));

            service.retryPayment(order.getUuid(), jwt);

            // Assert the retry hits processPayment with exactly the stored (already-discounted) total.
            // A buggy re-apply would have re-discounted 60 → 36 (BLACK_FRIDAY e.g. -40%).
            final ArgumentCaptor<Money> moneyCap = ArgumentCaptor.forClass(Money.class);
            verify(paymentService).processPayment(eq(order), eq(PaymentType.VISA), moneyCap.capture(), anyString());
            assertThat(moneyCap.getValue()).isEqualTo(originalTotal);
            assertThat(moneyCap.getValue().getAmount()).isEqualByComparingTo("60.00");
        }
    }

    // =================================================================
    @Nested
    @DisplayName("getAll / getByUUID / getByUUIDs (ownership-checked reads, admin bypass)")
    class Reads {

        /** Matches the principal name installed by {@link #setAuthenticatedRole(String)}. */
        private static final String CALLER = "test-user";
        private static final String FOREIGNER = "someone-else";

        private OrderEntity ownedBy(final String kc) {
            return OrderEntityBuilder.aValidOrderBuilder()
                    .userEntity(UserEntityBuilder.aValidUserBuilder().keycloakId(kc).build())
                    .build();
        }

        @Test
        @DisplayName("getAll returns all orders mapped to response DTOs (admin-only, unchanged)")
        void getAll_happy() {
            final OrderEntity o1 = OrderEntityBuilder.aValidOrder();
            final OrderEntity o2 = OrderEntityBuilder.aValidOrder();
            when(orderRepository.findAll()).thenReturn(List.of(o1, o2));

            final Collection<OrderResponseDto> all = service.getAll();
            assertThat(all).hasSize(2);
            verify(orderMapper, times(2)).mapFromEntityToResponseDto(any(OrderEntity.class));
        }

        // ---- getByUUID(UUID) — now ownership-checked with admin bypass ----

        @Test
        @DisplayName("getByUUID: owning USER gets the order")
        void getByUUID_ownerOk() {
            setAuthenticatedRole("ROLE_USER");
            final OrderEntity o = ownedBy(CALLER);
            when(orderRepository.findByUuid(o.getUuid())).thenReturn(Optional.of(o));

            assertThat(service.getByUUID(o.getUuid())).isNotNull();
            verify(orderMapper).mapFromEntityToResponseDto(o);
        }

        @Test
        @DisplayName("getByUUID: non-owner USER -> OrderDoesntBelongsToUserException (IDOR guard)")
        void getByUUID_nonOwnerThrows() {
            setAuthenticatedRole("ROLE_USER");
            final OrderEntity o = ownedBy(FOREIGNER);
            when(orderRepository.findByUuid(o.getUuid())).thenReturn(Optional.of(o));

            assertThatThrownBy(() -> service.getByUUID(o.getUuid()))
                    .isInstanceOf(OrderDoesntBelongsToUserException.class);
            verify(orderMapper, never()).mapFromEntityToResponseDto(any());
        }

        @Test
        @DisplayName("getByUUID: ADMIN bypasses the ownership check")
        void getByUUID_adminBypass() {
            setAuthenticatedRole("ROLE_ADMIN");
            final OrderEntity o = ownedBy(FOREIGNER);
            when(orderRepository.findByUuid(o.getUuid())).thenReturn(Optional.of(o));

            assertThat(service.getByUUID(o.getUuid())).isNotNull();
            verify(orderMapper).mapFromEntityToResponseDto(o);
        }

        @Test
        @DisplayName("getByUUID throws when not found (before ownership check)")
        void getByUUID_notFound() {
            setAuthenticatedRole("ROLE_USER");
            final UUID missing = UUID.randomUUID();
            when(orderRepository.findByUuid(missing)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getByUUID(missing))
                    .isInstanceOf(OrderNotFoundException.class);
        }

        // ---- getByUUIDs(Collection) — throws 403 on the whole batch if any UUID is foreign ----

        @Test
        @DisplayName("getByUUIDs: USER owning every order -> all returned")
        void getByUUIDs_allOwned() {
            setAuthenticatedRole("ROLE_USER");
            final OrderEntity o1 = ownedBy(CALLER);
            final OrderEntity o2 = ownedBy(CALLER);
            when(orderRepository.findAllByUuidIn(any())).thenReturn(List.of(o1, o2));

            assertThat(service.getByUUIDs(List.of(o1.getUuid(), o2.getUuid()))).hasSize(2);
        }

        @Test
        @DisplayName("getByUUIDs: any foreign UUID -> 403 on the whole batch (USER)")
        void getByUUIDs_foreignThrows() {
            setAuthenticatedRole("ROLE_USER");
            final OrderEntity mine = ownedBy(CALLER);
            final OrderEntity foreign = ownedBy(FOREIGNER);
            when(orderRepository.findAllByUuidIn(any())).thenReturn(List.of(mine, foreign));

            assertThatThrownBy(() -> service.getByUUIDs(List.of(mine.getUuid(), foreign.getUuid())))
                    .isInstanceOf(OrderDoesntBelongsToUserException.class);
            verify(orderMapper, never()).mapFromEntityToResponseDto(any());
        }

        @Test
        @DisplayName("getByUUIDs: ADMIN bypasses ownership")
        void getByUUIDs_adminBypass() {
            setAuthenticatedRole("ROLE_ADMIN");
            final OrderEntity foreign = ownedBy(FOREIGNER);
            when(orderRepository.findAllByUuidIn(any())).thenReturn(List.of(foreign));

            assertThat(service.getByUUIDs(List.of(foreign.getUuid()))).hasSize(1);
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
        @DisplayName("retryPayment builds a unique key via the (String, List) overload — context contains 'retry' + monotonic attempt counter")
        void retryPayment_usesListOverloadWithRetryActionAndCounter() {
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

            // H-3 fix: the constant (String, String) "retry" key is gone. Each retry now mixes
            // the 'retry' marker + a monotonic attempt counter (1 prior FAILED attempt → "2")
            // + a capture timestamp into the (String, List) overload context, so every retry of
            // the same order produces a DISTINCT idempotency key.
            verify(idempotencyKeyService).generateKey(
                    eq(order.getUuid().toString()),
                    argThat((List<String> ctx) -> ctx.contains("retry") && ctx.contains("2")));
        }
    }

    // =================================================================
    @Nested
    @DisplayName("getStatusByUUID — lightweight ownership-checked status read")
    class GetStatusByUUID {

        @Test
        @DisplayName("happy: caller owns the order -> returns OrderStatusDto with current status")
        void ownerCanReadStatus() {
            final UUID uuid = UUID.randomUUID();
            final UserEntity owner = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).build();
            final OrderEntity order = OrderEntityBuilder.aValidOrderBuilder()
                    .uuid(uuid).status(OrderStatus.PAID).userEntity(owner).build();
            when(orderRepository.findByUuid(uuid)).thenReturn(Optional.of(order));

            final com.novatech.cybertech.dto.response.order.OrderStatusDto dto =
                    service.getStatusByUUID(uuid, keycloakId);

            assertThat(dto.uuid()).isEqualTo(uuid);
            assertThat(dto.status()).isEqualTo(OrderStatus.PAID);
        }

        @Test
        @DisplayName("missing order -> OrderNotFoundException")
        void missingOrderThrows() {
            final UUID uuid = UUID.randomUUID();
            when(orderRepository.findByUuid(uuid)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getStatusByUUID(uuid, keycloakId))
                    .isInstanceOf(OrderNotFoundException.class);
        }

        @Test
        @DisplayName("non-owner caller -> OrderDoesntBelongsToUserException (IDOR guard)")
        void nonOwnerRejected() {
            final UUID uuid = UUID.randomUUID();
            final UserEntity stranger = UserEntityBuilder.aValidUserBuilder().keycloakId("kc-stranger").build();
            final OrderEntity order = OrderEntityBuilder.aValidOrderBuilder()
                    .uuid(uuid).status(OrderStatus.PAID).userEntity(stranger).build();
            when(orderRepository.findByUuid(uuid)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> service.getStatusByUUID(uuid, keycloakId))
                    .isInstanceOf(OrderDoesntBelongsToUserException.class);
        }

        /**
         * BUG-5 FIX: getStatusByUUID was missing the {@code !isCurrentCallerAdmin()} guard that
         * {@link OrderManagementServiceImp#getByUUID(UUID, String)} already had — admin support
         * tooling polling status on a customer's order would receive HTTP 403. The fix mirrors
         * {@code getByUUID}'s admin escape-hatch.
         */
        @Test
        @DisplayName("BUG-5 FIX: ADMIN caller bypasses ownership check, status returned for foreign user's order")
        void getStatusByUuidAsAdminShouldReturnAnyOrderStatus() {
            setAuthenticatedRole("ROLE_ADMIN");
            final UUID uuid = UUID.randomUUID();
            final UserEntity stranger = UserEntityBuilder.aValidUserBuilder().keycloakId("kc-stranger").build();
            final OrderEntity order = OrderEntityBuilder.aValidOrderBuilder()
                    .uuid(uuid).status(OrderStatus.SHIPPED).userEntity(stranger).build();
            when(orderRepository.findByUuid(uuid)).thenReturn(Optional.of(order));

            // Caller passes its own (non-owner) keycloakId — admin role should override the IDOR check.
            final com.novatech.cybertech.dto.response.order.OrderStatusDto dto =
                    service.getStatusByUUID(uuid, keycloakId);

            assertThat(dto.uuid()).isEqualTo(uuid);
            assertThat(dto.status()).isEqualTo(OrderStatus.SHIPPED);
        }

        @Test
        @DisplayName("regression: USER caller without ROLE_ADMIN reading another user's order is still rejected")
        void getStatusByUuidAsUserShouldThrowWhenOrderBelongsToOtherUser() {
            setAuthenticatedRole("ROLE_USER");
            final UUID uuid = UUID.randomUUID();
            final UserEntity stranger = UserEntityBuilder.aValidUserBuilder().keycloakId("kc-stranger").build();
            final OrderEntity order = OrderEntityBuilder.aValidOrderBuilder()
                    .uuid(uuid).status(OrderStatus.PAID).userEntity(stranger).build();
            when(orderRepository.findByUuid(uuid)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> service.getStatusByUUID(uuid, keycloakId))
                    .isInstanceOf(OrderDoesntBelongsToUserException.class);
        }

        @Test
        @DisplayName("regression: USER caller reading their OWN order succeeds")
        void getStatusByUuidAsUserShouldSucceedWhenOrderBelongsToCaller() {
            setAuthenticatedRole("ROLE_USER");
            final UUID uuid = UUID.randomUUID();
            final UserEntity owner = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).build();
            final OrderEntity order = OrderEntityBuilder.aValidOrderBuilder()
                    .uuid(uuid).status(OrderStatus.PAID).userEntity(owner).build();
            when(orderRepository.findByUuid(uuid)).thenReturn(Optional.of(order));

            final com.novatech.cybertech.dto.response.order.OrderStatusDto dto =
                    service.getStatusByUUID(uuid, keycloakId);

            assertThat(dto.uuid()).isEqualTo(uuid);
            assertThat(dto.status()).isEqualTo(OrderStatus.PAID);
        }
    }

    // =================================================================
    @Nested
    @DisplayName("findMyOrders — Frontend-gap #1 paginated read of caller's own orders")
    class FindMyOrders {

        @Test
        @DisplayName("happy path: forwards keycloakId + pageable to repo and maps each entity through the mapper")
        void findMyOrders_happyPath_mapsThroughRepository() {
            final org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 20);
            final OrderEntity entity = OrderEntityBuilder.aValidOrder();
            final org.springframework.data.domain.Page<OrderEntity> page =
                    new org.springframework.data.domain.PageImpl<>(List.of(entity));
            when(orderRepository.findByUserKeycloakIdAndOptionalStatuses(eq(keycloakId), eq(null), eq(pageable)))
                    .thenReturn(page);

            final org.springframework.data.domain.Page<OrderResponseDto> result =
                    service.findMyOrders(keycloakId, pageable, null);

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent()).hasSize(1);
            verify(orderMapper).mapFromEntityToResponseDto(entity);
        }

        @Test
        @DisplayName("empty status set is normalised to null so the JPQL :statuses IS NULL OR ... short-circuits")
        void findMyOrders_emptyStatusSet_normalisedToNull() {
            final org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 20);
            when(orderRepository.findByUserKeycloakIdAndOptionalStatuses(eq(keycloakId), eq(null), eq(pageable)))
                    .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));

            service.findMyOrders(keycloakId, pageable, java.util.Set.of());

            // The skeptical assertion: the impl MUST forward null to the repository when the caller passes an empty set.
            ArgumentCaptor<Collection<OrderStatus>> statusesCaptor = ArgumentCaptor.forClass(Collection.class);
            verify(orderRepository).findByUserKeycloakIdAndOptionalStatuses(eq(keycloakId), statusesCaptor.capture(), eq(pageable));
            assertThat(statusesCaptor.getValue()).isNull();
        }

        @Test
        @DisplayName("non-empty status set is forwarded verbatim — service does not transform user-supplied filters")
        void findMyOrders_filterByStatus_forwardedVerbatim() {
            final org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 20);
            final java.util.Set<OrderStatus> statuses = java.util.Set.of(OrderStatus.PAID, OrderStatus.SHIPPED);
            when(orderRepository.findByUserKeycloakIdAndOptionalStatuses(eq(keycloakId), eq(statuses), eq(pageable)))
                    .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));

            service.findMyOrders(keycloakId, pageable, statuses);

            ArgumentCaptor<Collection<OrderStatus>> statusesCaptor = ArgumentCaptor.forClass(Collection.class);
            verify(orderRepository).findByUserKeycloakIdAndOptionalStatuses(eq(keycloakId), statusesCaptor.capture(), eq(pageable));
            assertThat(statusesCaptor.getValue()).containsExactlyInAnyOrder(OrderStatus.PAID, OrderStatus.SHIPPED);
        }

        @Test
        @DisplayName("empty result page surfaces as empty, mapper never invoked")
        void findMyOrders_emptyPage_returnsEmpty() {
            final org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 20);
            when(orderRepository.findByUserKeycloakIdAndOptionalStatuses(eq(keycloakId), eq(null), eq(pageable)))
                    .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));

            final org.springframework.data.domain.Page<OrderResponseDto> result =
                    service.findMyOrders(keycloakId, pageable, null);

            assertThat(result.getTotalElements()).isZero();
            assertThat(result.getContent()).isEmpty();
            verify(orderMapper, never()).mapFromEntityToResponseDto(any(OrderEntity.class));
        }
    }

    // =================================================================
    @Nested
    @DisplayName("findAllPaged — Frontend-gap #2 admin paginated read with optional filters")
    class FindAllPaged {

        @Test
        @DisplayName("happy path: forwards both nullable filters to repo and maps entity → DTO")
        void findAllPaged_noFilters_returnsAllOrders() {
            final org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 20);
            final OrderEntity entity = OrderEntityBuilder.aValidOrder();
            when(orderRepository.findAllByOptionalStatusesAndUserKeycloakId(eq(null), eq(null), eq(pageable)))
                    .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(entity)));

            final org.springframework.data.domain.Page<OrderResponseDto> result =
                    service.findAllPaged(null, null, pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
            verify(orderMapper).mapFromEntityToResponseDto(entity);
        }

        @Test
        @DisplayName("empty status set is normalised to null (mirrors findMyOrders) so :statuses IS NULL short-circuits")
        void findAllPaged_emptyStatusSet_normalisedToNull() {
            final org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 20);
            when(orderRepository.findAllByOptionalStatusesAndUserKeycloakId(eq(null), eq("kc-target"), eq(pageable)))
                    .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));

            service.findAllPaged(java.util.Set.of(), "kc-target", pageable);

            ArgumentCaptor<Collection<OrderStatus>> statusesCaptor = ArgumentCaptor.forClass(Collection.class);
            verify(orderRepository).findAllByOptionalStatusesAndUserKeycloakId(statusesCaptor.capture(), eq("kc-target"), eq(pageable));
            assertThat(statusesCaptor.getValue()).isNull();
        }

        @Test
        @DisplayName("status filter only — userKeycloakId stays null and is forwarded as-is")
        void findAllPaged_statusFilterOnly_userIdNull() {
            final org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 20);
            final java.util.Set<OrderStatus> statuses = java.util.Set.of(OrderStatus.CANCELED);
            when(orderRepository.findAllByOptionalStatusesAndUserKeycloakId(eq(statuses), eq(null), eq(pageable)))
                    .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));

            service.findAllPaged(statuses, null, pageable);

            verify(orderRepository).findAllByOptionalStatusesAndUserKeycloakId(eq(statuses), eq(null), eq(pageable));
        }

        @Test
        @DisplayName("user filter only — statuses stay null, the userKeycloakId reaches the repo verbatim")
        void findAllPaged_userFilterOnly_statusesNull() {
            final org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 20);
            when(orderRepository.findAllByOptionalStatusesAndUserKeycloakId(eq(null), eq("kc-only-user"), eq(pageable)))
                    .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));

            service.findAllPaged(null, "kc-only-user", pageable);

            verify(orderRepository).findAllByOptionalStatusesAndUserKeycloakId(eq(null), eq("kc-only-user"), eq(pageable));
        }

        @Test
        @DisplayName("empty result: mapper never called, surface stays an empty page")
        void findAllPaged_emptyPage_returnsEmpty() {
            final org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 20);
            when(orderRepository.findAllByOptionalStatusesAndUserKeycloakId(eq(null), eq(null), eq(pageable)))
                    .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));

            final org.springframework.data.domain.Page<OrderResponseDto> result =
                    service.findAllPaged(null, null, pageable);

            assertThat(result.getTotalElements()).isZero();
            verify(orderMapper, never()).mapFromEntityToResponseDto(any(OrderEntity.class));
        }
    }
}
