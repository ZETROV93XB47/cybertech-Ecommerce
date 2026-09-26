package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.dto.data.OrderValidationDto;
import com.novatech.cybertech.dto.request.order.OrderPlacingRequestDto;
import com.novatech.cybertech.dto.request.order.PriceCalculationRequestDto;
import com.novatech.cybertech.dto.response.order.PriceCalculationResultDto;
import com.novatech.cybertech.entities.CartEntity;
import com.novatech.cybertech.entities.CartItemEntity;
import com.novatech.cybertech.entities.OrderEntity;
import com.novatech.cybertech.entities.ProductEntity;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.entities.enums.DiscountType;
import com.novatech.cybertech.entities.enums.OrderStatus;
import com.novatech.cybertech.entities.enums.ShippingProvider;
import com.novatech.cybertech.entities.enums.ShippingType;
import com.novatech.cybertech.entities.valueObjects.CurrencyCode;
import com.novatech.cybertech.exceptions.BankCardNotFoundException;
import com.novatech.cybertech.exceptions.CartNotFoundException;
import com.novatech.cybertech.exceptions.NotEnoughStockException;
import com.novatech.cybertech.exceptions.UserNotFoundException;
import com.novatech.cybertech.fixtures.builders.BankCardEntityBuilder;
import com.novatech.cybertech.fixtures.builders.CartEntityBuilder;
import com.novatech.cybertech.fixtures.builders.CartItemEntityBuilder;
import com.novatech.cybertech.fixtures.builders.ProductEntityBuilder;
import com.novatech.cybertech.fixtures.builders.UserEntityBuilder;
import com.novatech.cybertech.fixtures.dto.OrderDtoFixtures;
import com.novatech.cybertech.repositories.OrderRepository;
import com.novatech.cybertech.repositories.UserRepository;
import com.novatech.cybertech.services.core.OrderPriceCalculationService;
import com.novatech.cybertech.services.core.StockService;
import com.novatech.cybertech.validator.core.OrderValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OrderCreationTransactionalDelegateImp}.
 *
 * <p>Covers everything {@code OrderManagementServiceImp.placeOrder} used to test directly before
 * being split into a delegate — see {@code OrderManagementServiceImpTest.PlaceOrder} for the
 * orchestration-only tests (payment attempt, Stripe-infra-failure degradation, event publishing)
 * that stayed there.
 */
@ExtendWith(MockitoExtension.class)
class OrderCreationTransactionalDelegateImpTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private StockService stockService;
    @Mock
    private OrderValidator orderValidatorChain;
    @Mock
    private OrderPriceCalculationService orderPriceCalculationService;

    private OrderCreationTransactionalDelegateImp delegate;

    private String keycloakId;

    @BeforeEach
    void setUp() {
        delegate = new OrderCreationTransactionalDelegateImp(
                userRepository, orderRepository, stockService, orderValidatorChain, orderPriceCalculationService);
        keycloakId = "kc-" + UUID.randomUUID();

        lenient().when(orderPriceCalculationService.calculate(any(PriceCalculationRequestDto.class)))
                .thenReturn(PriceCalculationResultDto.builder()
                        .baseAmount(new BigDecimal("10.00"))
                        .discountAmount(BigDecimal.ZERO)
                        .finalAmount(new BigDecimal("10.00"))
                        .currencyCode(CurrencyCode.EUR)
                        .discountType(DiscountType.NO_DISCOUNT)
                        .build());
    }

    private UserEntity userWithCart(final ProductEntity product, final int quantity) {
        final UserEntity user = UserEntityBuilder.aValidUserBuilder()
                .keycloakId(keycloakId)
                .bankCardEntity(BankCardEntityBuilder.aValidBankCard())
                .build();
        final CartItemEntity item = CartItemEntityBuilder.aValidCartItemBuilder()
                .productEntity(product)
                .quantity(quantity)
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

    @Test
    @DisplayName("user missing in repo throws UserNotFoundException")
    void userMissing_throwsUserNotFound() {
        when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> delegate.createAndReserveStock(OrderDtoFixtures.aValidPlaceOrderRequest(), keycloakId))
                .isInstanceOf(UserNotFoundException.class);

        verifyNoInteractions(orderRepository, stockService);
    }

    @Test
    @DisplayName("user with null cart throws CartNotFoundException")
    void cartMissing_throwsCartNotFound() {
        when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(userNoCart()));

        assertThatThrownBy(() -> delegate.createAndReserveStock(OrderDtoFixtures.aValidPlaceOrderRequest(), keycloakId))
                .isInstanceOf(CartNotFoundException.class);

        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("user with empty cart throws CartNotFoundException")
    void cartEmpty_throwsCartNotFound() {
        when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(userWithEmptyCart()));

        assertThatThrownBy(() -> delegate.createAndReserveStock(OrderDtoFixtures.aValidPlaceOrderRequest(), keycloakId))
                .isInstanceOf(CartNotFoundException.class);

        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("user without a bank card throws BankCardNotFoundException before save")
    void noBankCard_throwsBankCardNotFound() {
        final ProductEntity product = ProductEntityBuilder.aValidProduct();
        final UserEntity user = userWithCart(product, 1);
        user.setBankCardEntity(null);
        when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> delegate.createAndReserveStock(OrderDtoFixtures.aValidPlaceOrderRequest(), keycloakId))
                .isInstanceOf(BankCardNotFoundException.class);

        verify(orderRepository, never()).save(any());
        verifyNoInteractions(orderValidatorChain);
    }

    @Test
    @DisplayName("validator rejects -> exception bubbles, no save")
    void validatorRejects_propagates() {
        final ProductEntity product = ProductEntityBuilder.aValidProduct();
        final UserEntity user = userWithCart(product, 1);
        when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));
        doThrow(new IllegalStateException("validator-fail")).when(orderValidatorChain).validate(any(OrderValidationDto.class));

        assertThatThrownBy(() -> delegate.createAndReserveStock(OrderDtoFixtures.aValidPlaceOrderRequest(), keycloakId))
                .isInstanceOf(IllegalStateException.class);

        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("stock reservation fails -> NotEnoughStockException bubbles")
    void stockReservationFails_propagates() {
        final ProductEntity product = ProductEntityBuilder.aValidProduct();
        final UserEntity user = userWithCart(product, 1);
        when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));
        when(orderRepository.save(any(OrderEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new NotEnoughStockException("nope")).when(stockService).reserveStock(any(UUID.class), any());

        assertThatThrownBy(() -> delegate.createAndReserveStock(OrderDtoFixtures.aValidPlaceOrderRequest(), keycloakId))
                .isInstanceOf(NotEnoughStockException.class);
    }

    @Test
    @DisplayName("happy path: validates, saves order, reserves stock, in order")
    void happyPath_validatesSavesReserves_inOrder() {
        final ProductEntity product = ProductEntityBuilder.aValidProductBuilder().price(new BigDecimal("50.00")).build();
        final UserEntity user = userWithCart(product, 2);
        when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));
        when(orderRepository.save(any(OrderEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        final OrderEntity result = delegate.createAndReserveStock(OrderDtoFixtures.aValidPlaceOrderRequest(), keycloakId);

        assertThat(result).isNotNull();
        assertThat(result.getUserEntity()).isEqualTo(user);

        final InOrder ord = inOrder(orderValidatorChain, orderRepository, stockService);
        ord.verify(orderValidatorChain).validate(any(OrderValidationDto.class));
        ord.verify(orderRepository).save(any(OrderEntity.class));
        ord.verify(stockService).reserveStock(any(UUID.class), any());
    }

    @Test
    @DisplayName("status of the saved order is AWAITING_PAYMENT")
    void savedOrderStatusIsAwaitingPayment() {
        final ProductEntity product = ProductEntityBuilder.aValidProduct();
        final UserEntity user = userWithCart(product, 1);
        when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));

        final ArgumentCaptor<OrderEntity> cap = ArgumentCaptor.forClass(OrderEntity.class);
        when(orderRepository.save(cap.capture())).thenAnswer(inv -> inv.getArgument(0));

        delegate.createAndReserveStock(OrderDtoFixtures.aValidPlaceOrderRequest(), keycloakId);

        assertThat(cap.getValue().getStatus()).isEqualTo(OrderStatus.AWAITING_PAYMENT);
    }

    @Test
    @DisplayName("total computed via OrderPriceCalculationService and saved as Money.EUR")
    void totalAmountComputedAndEurByDefault() {
        final ProductEntity product = ProductEntityBuilder.aValidProductBuilder().price(new BigDecimal("12.50")).build();
        final UserEntity user = userWithCart(product, 3);
        when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));
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

        delegate.createAndReserveStock(OrderDtoFixtures.aValidPlaceOrderRequest(), keycloakId);

        final OrderEntity saved = cap.getValue();
        assertThat(saved.getTotalAmount().getAmount()).isEqualByComparingTo("37.50");
        assertThat(saved.getTotalAmount().getCurrencyCode()).isEqualTo(CurrencyCode.EUR);
    }

    @Test
    @DisplayName("SHIPPING-INT: forwards shippingProvider + shippingType to OrderPriceCalculationService")
    void forwardsShippingProviderAndTypeToPriceCalc() {
        final ProductEntity product = ProductEntityBuilder.aValidProduct();
        final UserEntity user = userWithCart(product, 1);
        when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));
        when(orderRepository.save(any(OrderEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        final OrderPlacingRequestDto req = OrderDtoFixtures.aValidPlaceOrderRequestBuilder()
                .shippingProvider(ShippingProvider.FEDEX)
                .shippingType(ShippingType.EXPRESS)
                .build();

        delegate.createAndReserveStock(req, keycloakId);

        final ArgumentCaptor<PriceCalculationRequestDto> cap = ArgumentCaptor.forClass(PriceCalculationRequestDto.class);
        verify(orderPriceCalculationService).calculate(cap.capture());
        assertThat(cap.getValue().getShippingProvider()).isEqualTo(ShippingProvider.FEDEX);
        assertThat(cap.getValue().getShippingType()).isEqualTo(ShippingType.EXPRESS);
    }
}
