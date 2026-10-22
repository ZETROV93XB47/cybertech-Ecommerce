package com.novatech.cybertech.services.implementation.order;

import com.novatech.cybertech.dto.data.DiscountContext;
import com.novatech.cybertech.dto.request.order.OrderQuoteRequestDto;
import com.novatech.cybertech.dto.response.order.OrderQuoteResponseDto;
import com.novatech.cybertech.entities.CartEntity;
import com.novatech.cybertech.entities.CartItemEntity;
import com.novatech.cybertech.entities.OrderEntity;
import com.novatech.cybertech.entities.ProductEntity;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.entities.enums.DiscountCalculationType;
import com.novatech.cybertech.entities.enums.ShippingProvider;
import com.novatech.cybertech.entities.enums.ShippingType;
import com.novatech.cybertech.exceptions.CartIsEmptyException;
import com.novatech.cybertech.exceptions.DiscountTypeNotActiveException;
import com.novatech.cybertech.factory.DiscountStrategyFactory;
import com.novatech.cybertech.factory.ShippingProviderStrategyFactory;
import com.novatech.cybertech.fixtures.builders.BankCardEntityBuilder;
import com.novatech.cybertech.fixtures.builders.CartEntityBuilder;
import com.novatech.cybertech.fixtures.builders.CartItemEntityBuilder;
import com.novatech.cybertech.fixtures.builders.ProductEntityBuilder;
import com.novatech.cybertech.fixtures.builders.UserEntityBuilder;
import com.novatech.cybertech.fixtures.dto.OrderDtoFixtures;
import com.novatech.cybertech.mappers.entity.OrderMapper;
import com.novatech.cybertech.repositories.OrderRepository;
import com.novatech.cybertech.repositories.ProductRepository;
import com.novatech.cybertech.repositories.UserRepository;
import com.novatech.cybertech.services.core.CartService;
import com.novatech.cybertech.services.core.DiscountCampaignService;
import com.novatech.cybertech.services.core.IdempotencyKeyServiceGenerator;
import com.novatech.cybertech.services.core.OrderCancellationTransactionalDelegate;
import com.novatech.cybertech.services.core.PaymentService;
import com.novatech.cybertech.services.core.StockService;
import com.novatech.cybertech.services.implementation.DHLShippingProviderService;
import com.novatech.cybertech.services.implementation.FedexShippingProviderService;
import com.novatech.cybertech.services.implementation.OrderCreationTransactionalDelegateImp;
import com.novatech.cybertech.services.implementation.OrderManagementServiceImp;
import com.novatech.cybertech.services.implementation.OrderPriceCalculationServiceImp;
import com.novatech.cybertech.strategy.discount.BuyOneGetOneFreeDiscountStrategy;
import com.novatech.cybertech.strategy.discount.FixedAmountDiscountStrategy;
import com.novatech.cybertech.strategy.discount.PercentageDiscountStrategy;
import com.novatech.cybertech.validator.core.OrderValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.oauth2.jwt.Jwt;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.novatech.cybertech.constants.CyberTechAppConstants.NO_DISCOUNT_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@code OrderManagementService#quote}: wired on the REAL price calculation (strategies, DHL/FedEx
 * tariffs) and compared with the REAL {@link OrderCreationTransactionalDelegateImp} pricing the
 * same cart — the quote must equal the total of the order placed right after.
 */
@ExtendWith(MockitoExtension.class)
class OrderQuoteTest {

    private static final String KEYCLOAK_ID = "kc-quote";
    private static final String BLACK_FRIDAY = "BLACK_FRIDAY";

    @Mock private CartService cartService;
    @Mock private StockService stockService;
    @Mock private PaymentService paymentService;
    @Mock private UserRepository userRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private ProductRepository productRepository;
    @Mock private OrderValidator orderValidatorChain;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private IdempotencyKeyServiceGenerator idempotencyKeyService;
    @Mock private OrderCancellationTransactionalDelegate orderCancellationTransactionalDelegate;
    @Mock private DiscountCampaignService discountCampaignService;

    private OrderManagementServiceImp service;
    private OrderCreationTransactionalDelegateImp creationDelegate;
    private Jwt jwt;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        final OrderPriceCalculationServiceImp priceService = new OrderPriceCalculationServiceImp(
                new DiscountStrategyFactory(Map.of(
                        DiscountCalculationType.PERCENTAGE, new PercentageDiscountStrategy(),
                        DiscountCalculationType.FIXED_AMOUNT, new FixedAmountDiscountStrategy(),
                        DiscountCalculationType.BUY_ONE_GET_ONE_FREE, new BuyOneGetOneFreeDiscountStrategy())),
                discountCampaignService,
                new ShippingProviderStrategyFactory(Map.of(
                        ShippingProvider.DHL, new DHLShippingProviderService(),
                        ShippingProvider.FEDEX, new FedexShippingProviderService())));
        creationDelegate = new OrderCreationTransactionalDelegateImp(userRepository, orderRepository, stockService, orderValidatorChain, priceService);
        service = new OrderManagementServiceImp(Mappers.getMapper(OrderMapper.class), cartService, stockService, paymentService,
                userRepository, orderRepository, productRepository, orderValidatorChain, eventPublisher, idempotencyKeyService,
                priceService, orderCancellationTransactionalDelegate, creationDelegate);

        jwt = mock(Jwt.class);
        lenient().when(jwt.getSubject()).thenReturn(KEYCLOAK_ID);
        user = UserEntityBuilder.aValidUserBuilder().keycloakId(KEYCLOAK_ID).bankCardEntity(BankCardEntityBuilder.aValidBankCard()).build();
        lenient().when(userRepository.findByKeycloakId(KEYCLOAK_ID)).thenReturn(Optional.of(user));
    }

    private void cartWith(final String price, final int quantity) {
        final ProductEntity product = ProductEntityBuilder.aValidProductBuilder().price(new BigDecimal(price)).build();
        final CartItemEntity item = CartItemEntityBuilder.aValidCartItemBuilder().productEntity(product).quantity(quantity).build();
        final CartEntity cart = CartEntityBuilder.aValidCartBuilder().userEntity(user).cartItems(new ArrayList<>(List.of(item))).build();
        user.setCartEntity(cart);
    }

    private void blackFriday(final String minOrderAmount) {
        when(discountCampaignService.getActiveDiscountContext(BLACK_FRIDAY)).thenReturn(DiscountContext.builder()
                .discountKey(BLACK_FRIDAY).calculationType(DiscountCalculationType.PERCENTAGE)
                .percentage(new BigDecimal("20")).minOrderAmount(minOrderAmount == null ? null : new BigDecimal(minOrderAmount)).build());
    }

    private static OrderQuoteRequestDto quoteRequest(final String discountKey, final ShippingType type) {
        return OrderQuoteRequestDto.builder().discountKey(discountKey).shippingProvider(ShippingProvider.DHL).shippingType(type).build();
    }

    /** Places the order (real delegate, repositories mocked) with the same options and returns its total. */
    private BigDecimal placedTotal(final String discountKey, final ShippingType type) {
        when(orderRepository.save(any(OrderEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        return creationDelegate.createAndReserveStock(OrderDtoFixtures.aValidPlaceOrderRequestBuilder()
                .discountKey(discountKey).shippingProvider(ShippingProvider.DHL).shippingType(type).build(), KEYCLOAK_ID).getTotalAmount().getAmount();
    }

    @Test
    @DisplayName("campaign reached: full breakdown, discountApplied=true, equals the total of the order placed right after")
    void discountApplied_equalsPlacedOrderTotal() {
        cartWith("100.00", 2);
        blackFriday("150.00");

        final OrderQuoteResponseDto quote = service.quote(quoteRequest(BLACK_FRIDAY, ShippingType.EXPRESS), jwt);

        assertThat(quote.getSubtotalAmount()).isEqualByComparingTo("200.00");
        assertThat(quote.getDiscountKey()).isEqualTo(BLACK_FRIDAY);
        assertThat(quote.getDiscountCalculationType()).isEqualTo(DiscountCalculationType.PERCENTAGE);
        assertThat(quote.getDiscountAmount()).isEqualByComparingTo("40.00");
        assertThat(quote.isDiscountApplied()).isTrue();
        assertThat(quote.getMinOrderAmount()).isEqualByComparingTo("150.00");
        assertThat(quote.getShippingCost()).isEqualByComparingTo("25.00");
        assertThat(quote.getTotalAmount()).isEqualByComparingTo("185.00");
        assertThat(quote.getCurrency()).isEqualTo("EUR");
        // No side effect: nothing ordered, reserved or charged.
        verifyNoInteractions(orderRepository, stockService, paymentService, eventPublisher, cartService);

        assertThat(quote.getTotalAmount()).isEqualByComparingTo(placedTotal(BLACK_FRIDAY, ShippingType.EXPRESS));
    }

    @Test
    @DisplayName("below the campaign minimum: discountApplied=false, discount 0, key kept, equals the placed total")
    void belowMinimum_discountNotApplied() {
        cartWith("50.00", 2);
        blackFriday("150.00");

        final OrderQuoteResponseDto quote = service.quote(quoteRequest(BLACK_FRIDAY, ShippingType.STANDARD), jwt);

        assertThat(quote.isDiscountApplied()).isFalse();
        assertThat(quote.getDiscountAmount()).isEqualByComparingTo("0.00");
        assertThat(quote.getDiscountKey()).isEqualTo(BLACK_FRIDAY);
        assertThat(quote.getMinOrderAmount()).isEqualByComparingTo("150.00");
        assertThat(quote.getTotalAmount()).isEqualByComparingTo("115.00");
        assertThat(quote.getTotalAmount()).isEqualByComparingTo(placedTotal(BLACK_FRIDAY, ShippingType.STANDARD));
    }

    @Test
    @DisplayName("NO_DISCOUNT (default): discountApplied=false, NONE, no minimum, campaign never looked up")
    void noDiscount_default() {
        cartWith("80.00", 1);

        final OrderQuoteResponseDto quote = service.quote(OrderQuoteRequestDto.builder()
                .shippingProvider(ShippingProvider.DHL).shippingType(ShippingType.STANDARD).build(), jwt);

        assertThat(quote.getDiscountKey()).isEqualTo(NO_DISCOUNT_KEY);
        assertThat(quote.getDiscountCalculationType()).isEqualTo(DiscountCalculationType.NONE);
        assertThat(quote.isDiscountApplied()).isFalse();
        assertThat(quote.getMinOrderAmount()).isNull();
        assertThat(quote.getTotalAmount()).isEqualByComparingTo("95.00");
        verifyNoInteractions(discountCampaignService);
    }

    @Test
    @DisplayName("empty cart -> CartIsEmptyException (409 CART_IS_EMPTY), like placing the order")
    void emptyCart_throwsCartIsEmpty() {
        user.setCartEntity(CartEntityBuilder.aValidCartBuilder().userEntity(user).cartItems(new ArrayList<>()).build());

        assertThatThrownBy(() -> service.quote(quoteRequest(NO_DISCOUNT_KEY, ShippingType.STANDARD), jwt))
                .isInstanceOf(CartIsEmptyException.class);
        assertThatThrownBy(() -> creationDelegate.createAndReserveStock(OrderDtoFixtures.aValidPlaceOrderRequest(), KEYCLOAK_ID))
                .isInstanceOf(CartIsEmptyException.class);
    }

    @Test
    @DisplayName("no cart at all -> CartIsEmptyException too")
    void noCart_throwsCartIsEmpty() {
        user.setCartEntity(null);

        assertThatThrownBy(() -> service.quote(quoteRequest(NO_DISCOUNT_KEY, ShippingType.STANDARD), jwt))
                .isInstanceOf(CartIsEmptyException.class);
    }

    @Test
    @DisplayName("unknown or inactive campaign -> DiscountTypeNotActiveException (400), nothing else touched")
    void inactiveCampaign_throwsDiscountTypeNotActive() {
        cartWith("100.00", 1);
        when(discountCampaignService.getActiveDiscountContext("EXPIRED_PROMO")).thenThrow(new DiscountTypeNotActiveException("EXPIRED_PROMO is not active"));

        assertThatThrownBy(() -> service.quote(quoteRequest("EXPIRED_PROMO", ShippingType.STANDARD), jwt))
                .isInstanceOf(DiscountTypeNotActiveException.class);
        verifyNoInteractions(orderRepository, stockService, paymentService);
    }
}
