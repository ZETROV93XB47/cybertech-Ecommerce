package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.dto.data.DiscountContext;
import com.novatech.cybertech.dto.request.order.OrderPlacingRequestDto;
import com.novatech.cybertech.dto.response.order.OrderResponseDto;
import com.novatech.cybertech.entities.CartEntity;
import com.novatech.cybertech.entities.CartItemEntity;
import com.novatech.cybertech.entities.OrderEntity;
import com.novatech.cybertech.entities.ProductEntity;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.entities.enums.DiscountCalculationType;
import com.novatech.cybertech.entities.enums.ShippingProvider;
import com.novatech.cybertech.entities.enums.ShippingType;
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
import com.novatech.cybertech.repositories.UserRepository;
import com.novatech.cybertech.services.core.DiscountCampaignService;
import com.novatech.cybertech.services.core.StockService;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.novatech.cybertech.constants.CyberTechAppConstants.NO_DISCOUNT_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Price breakdown persisted on an order at creation and exposed by {@link OrderResponseDto}.
 *
 * <p>Wires the REAL {@link OrderPriceCalculationServiceImp}, discount strategies and shipping
 * services into {@link OrderCreationTransactionalDelegateImp} (only repositories, stock, validator
 * and the campaign lookup are mocked), then maps the saved entity with the real
 * {@link OrderMapper}: each case checks the persisted fields, the DTO fields and the invariant
 * {@code totalAmount = max(0, subtotalAmount - discountAmount) + shippingCost}.
 */
@ExtendWith(MockitoExtension.class)
class OrderPriceBreakdownTest {

    private static final String KEYCLOAK_ID = "kc-breakdown";
    private static final String BLACK_FRIDAY = "BLACK_FRIDAY";
    private static final String WELCOME_25 = "WELCOME_25";
    private static final String BOGO = "BUY_ONE_GET_ONE_FREE";

    @Mock private UserRepository userRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private StockService stockService;
    @Mock private OrderValidator orderValidatorChain;
    @Mock private DiscountCampaignService discountCampaignService;

    private final OrderMapper orderMapper = Mappers.getMapper(OrderMapper.class);

    private OrderCreationTransactionalDelegateImp delegate;

    @BeforeEach
    void setUp() {
        final DiscountStrategyFactory discountStrategyFactory = new DiscountStrategyFactory(Map.of(
                DiscountCalculationType.PERCENTAGE, new PercentageDiscountStrategy(),
                DiscountCalculationType.FIXED_AMOUNT, new FixedAmountDiscountStrategy(),
                DiscountCalculationType.BUY_ONE_GET_ONE_FREE, new BuyOneGetOneFreeDiscountStrategy()));
        final ShippingProviderStrategyFactory shippingFactory = new ShippingProviderStrategyFactory(Map.of(
                ShippingProvider.DHL, new DHLShippingProviderService(),
                ShippingProvider.FEDEX, new FedexShippingProviderService()));
        final OrderPriceCalculationServiceImp priceService =
                new OrderPriceCalculationServiceImp(discountStrategyFactory, discountCampaignService, shippingFactory);

        delegate = new OrderCreationTransactionalDelegateImp(userRepository, orderRepository, stockService, orderValidatorChain, priceService);
        lenient().when(orderRepository.save(any(OrderEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    /** One cart line per {@code price, quantity} pair. */
    private void cartWith(final Object... priceThenQuantity) {
        final UserEntity user = UserEntityBuilder.aValidUserBuilder()
                .keycloakId(KEYCLOAK_ID)
                .bankCardEntity(BankCardEntityBuilder.aValidBankCard())
                .build();
        final List<CartItemEntity> items = new ArrayList<>();
        for (int i = 0; i < priceThenQuantity.length; i += 2) {
            final ProductEntity product = ProductEntityBuilder.aValidProductBuilder()
                    .price(new BigDecimal((String) priceThenQuantity[i]))
                    .build();
            items.add(CartItemEntityBuilder.aValidCartItemBuilder().productEntity(product).quantity((Integer) priceThenQuantity[i + 1]).build());
        }
        final CartEntity cart = CartEntityBuilder.aValidCartBuilder().userEntity(user).cartItems(items).build();
        user.setCartEntity(cart);
        when(userRepository.findByKeycloakId(KEYCLOAK_ID)).thenReturn(Optional.of(user));
    }

    private OrderEntity place(final String discountKey, final ShippingType shippingType) {
        final OrderPlacingRequestDto req = OrderDtoFixtures.aValidPlaceOrderRequestBuilder()
                .discountKey(discountKey)
                .shippingProvider(ShippingProvider.DHL)
                .shippingType(shippingType)
                .build();
        return delegate.createAndReserveStock(req, KEYCLOAK_ID);
    }

    private void campaign(final DiscountContext context) {
        when(discountCampaignService.getActiveDiscountContext(context.discountKey())).thenReturn(context);
    }

    private static void assertBreakdown(final OrderResponseDto dto, final String subtotal, final String discount,
                                        final String shipping, final String total) {
        assertThat(dto.getSubtotalAmount()).isEqualByComparingTo(subtotal);
        assertThat(dto.getDiscountAmount()).isEqualByComparingTo(discount);
        assertThat(dto.getShippingCost()).isEqualByComparingTo(shipping);
        assertThat(dto.getTotalAmount()).isEqualByComparingTo(total);
        assertThat(dto.getCurrency()).isEqualTo("EUR");
        // Invariant: total = max(0, subtotal - discount) + shipping
        assertThat(dto.getTotalAmount()).isEqualByComparingTo(
                dto.getSubtotalAmount().subtract(dto.getDiscountAmount()).max(BigDecimal.ZERO).add(dto.getShippingCost()));
    }

    @Test
    @DisplayName("NO_DISCOUNT + STANDARD: no discount, 15 EUR shipping, calculation type NONE")
    void noDiscount_standard() {
        cartWith("50.00", 2);

        final OrderEntity order = place(NO_DISCOUNT_KEY, ShippingType.STANDARD);
        final OrderResponseDto dto = orderMapper.mapFromEntityToResponseDto(order);

        assertBreakdown(dto, "100.00", "0.00", "15.00", "115.00");
        assertThat(dto.getDiscountKey()).isEqualTo(NO_DISCOUNT_KEY);
        assertThat(dto.getDiscountCalculationType()).isEqualTo(DiscountCalculationType.NONE);
        assertThat(dto.getShippingProvider()).isEqualTo(ShippingProvider.DHL);
        assertThat(dto.getShippingType()).isEqualTo(ShippingType.STANDARD);
        assertThat(dto.getCreatedAt()).isEqualTo(order.getOrderDate());
        assertThat(dto.getOrderDate()).isEqualTo(order.getOrderDate().toLocalDate());
    }

    @Test
    @DisplayName("PERCENTAGE capped by maxDiscountAmount + EXPRESS: 20% of 200 = 40 capped to 30, +25 shipping; terms snapshotted")
    void percentageCapped_express() {
        cartWith("100.00", 2);
        campaign(DiscountContext.builder().discountKey(BLACK_FRIDAY).calculationType(DiscountCalculationType.PERCENTAGE)
                .percentage(new BigDecimal("20")).maxDiscountAmount(new BigDecimal("30.00")).minOrderAmount(new BigDecimal("50.00")).build());

        final OrderEntity order = place(BLACK_FRIDAY, ShippingType.EXPRESS);
        final OrderResponseDto dto = orderMapper.mapFromEntityToResponseDto(order);

        assertBreakdown(dto, "200.00", "30.00", "25.00", "195.00");
        assertThat(dto.getDiscountKey()).isEqualTo(BLACK_FRIDAY);
        assertThat(dto.getDiscountCalculationType()).isEqualTo(DiscountCalculationType.PERCENTAGE);
        assertThat(order.getDiscountTerms().getPercentage()).isEqualByComparingTo("20");
        assertThat(order.getDiscountTerms().getMaxDiscountAmount()).isEqualByComparingTo("30.00");
        assertThat(order.getDiscountTerms().getMinOrderAmount()).isEqualByComparingTo("50.00");
        assertThat(order.getDiscountTerms().getFixedAmount()).isNull();
    }

    @Test
    @DisplayName("FIXED_AMOUNT + STANDARD: 25 off 80, +15 shipping")
    void fixedAmount_standard() {
        cartWith("80.00", 1);
        campaign(DiscountContext.builder().discountKey(WELCOME_25).calculationType(DiscountCalculationType.FIXED_AMOUNT)
                .fixedAmount(new BigDecimal("25.00")).build());

        final OrderEntity order = place(WELCOME_25, ShippingType.STANDARD);
        final OrderResponseDto dto = orderMapper.mapFromEntityToResponseDto(order);

        assertBreakdown(dto, "80.00", "25.00", "15.00", "70.00");
        assertThat(dto.getDiscountCalculationType()).isEqualTo(DiscountCalculationType.FIXED_AMOUNT);
        assertThat(order.getDiscountTerms().getFixedAmount()).isEqualByComparingTo("25.00");
    }

    @Test
    @DisplayName("BUY_ONE_GET_ONE_FREE + EXPRESS: one free unit per pair on each line (3x40 -> 40 off, 1x10 -> 0), +25 shipping")
    void buyOneGetOneFree_express() {
        cartWith("40.00", 3, "10.00", 1);
        campaign(DiscountContext.builder().discountKey(BOGO).calculationType(DiscountCalculationType.BUY_ONE_GET_ONE_FREE).build());

        final OrderEntity order = place(BOGO, ShippingType.EXPRESS);
        final OrderResponseDto dto = orderMapper.mapFromEntityToResponseDto(order);

        assertBreakdown(dto, "130.00", "40.00", "25.00", "115.00");
        assertThat(dto.getDiscountCalculationType()).isEqualTo(DiscountCalculationType.BUY_ONE_GET_ONE_FREE);
        assertThat(dto.getOrderItems()).hasSize(2);
    }

    @Test
    @DisplayName("below minOrderAmount + STANDARD: discount 0 but discountKey and terms are kept on the order")
    void belowMinOrderAmount_keepsKeyWithZeroDiscount() {
        cartWith("50.00", 2);
        campaign(DiscountContext.builder().discountKey(BLACK_FRIDAY).calculationType(DiscountCalculationType.PERCENTAGE)
                .percentage(new BigDecimal("20")).minOrderAmount(new BigDecimal("200.00")).build());

        final OrderEntity order = place(BLACK_FRIDAY, ShippingType.STANDARD);
        final OrderResponseDto dto = orderMapper.mapFromEntityToResponseDto(order);

        assertBreakdown(dto, "100.00", "0.00", "15.00", "115.00");
        assertThat(dto.getDiscountKey()).isEqualTo(BLACK_FRIDAY);
        assertThat(dto.getDiscountCalculationType()).isEqualTo(DiscountCalculationType.PERCENTAGE);
        assertThat(order.getDiscountTerms().getMinOrderAmount()).isEqualByComparingTo("200.00");
    }

    @Test
    @DisplayName("each shipping type adds exactly the DHL tariff: STANDARD 15, EXPRESS 25")
    void shippingTypes() {
        Arrays.stream(ShippingType.values()).forEach(type -> {
            cartWith("10.00", 1);
            final OrderResponseDto dto = orderMapper.mapFromEntityToResponseDto(place(NO_DISCOUNT_KEY, type));
            final String expectedShipping = type == ShippingType.EXPRESS ? "25.00" : "15.00";
            assertBreakdown(dto, "10.00", "0.00", expectedShipping, new BigDecimal("10.00").add(new BigDecimal(expectedShipping)).toPlainString());
        });
    }
}
