package com.novatech.cybertech.services.implementation.order;

import com.novatech.cybertech.dto.data.DiscountContext;
import com.novatech.cybertech.dto.request.order.OrderUpdateRequestDto;
import com.novatech.cybertech.dto.request.orderItem.OrderItemCreateRequestDto;
import com.novatech.cybertech.entities.OrderEntity;
import com.novatech.cybertech.entities.OrderItemEntity;
import com.novatech.cybertech.entities.PaymentEntity;
import com.novatech.cybertech.entities.ProductEntity;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.entities.enums.DiscountCalculationType;
import com.novatech.cybertech.entities.enums.OrderStatus;
import com.novatech.cybertech.entities.enums.PaymentAttemptStatus;
import com.novatech.cybertech.entities.enums.ShippingProvider;
import com.novatech.cybertech.entities.enums.ShippingType;
import com.novatech.cybertech.entities.enums.TransactionType;
import com.novatech.cybertech.entities.valueObjects.CurrencyCode;
import com.novatech.cybertech.entities.valueObjects.DiscountTermsSnapshot;
import com.novatech.cybertech.entities.valueObjects.Money;
import com.novatech.cybertech.exceptions.DiscountTypeNotActiveException;
import com.novatech.cybertech.factory.DiscountStrategyFactory;
import com.novatech.cybertech.factory.ShippingProviderStrategyFactory;
import com.novatech.cybertech.fixtures.builders.BankCardEntityBuilder;
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
import com.novatech.cybertech.services.core.CartService;
import com.novatech.cybertech.services.core.DiscountCampaignService;
import com.novatech.cybertech.services.core.IdempotencyKeyServiceGenerator;
import com.novatech.cybertech.services.core.OrderCancellationTransactionalDelegate;
import com.novatech.cybertech.services.core.OrderCreationTransactionalDelegate;
import com.novatech.cybertech.services.core.PaymentService;
import com.novatech.cybertech.services.core.StockService;
import com.novatech.cybertech.services.implementation.DHLShippingProviderService;
import com.novatech.cybertech.services.implementation.FedexShippingProviderService;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.oauth2.jwt.Jwt;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@code updateOrder} keeps the discount the order was placed under.
 *
 * <p>Wires the REAL {@link OrderPriceCalculationServiceImp} (strategies, DHL/FedEx tariffs) and
 * the real {@link OrderMapper}; repositories, payment and stock are mocked. The reference order:
 * 2 x 100 EUR with BLACK_FRIDAY (20%, minimum 150), DHL STANDARD (15) → 200 - 40 + 15 = 175 EUR,
 * fully paid.
 */
@ExtendWith(MockitoExtension.class)
class OrderUpdateKeepsDiscountTest {

    private static final String BLACK_FRIDAY = "BLACK_FRIDAY";
    private static final String KEYCLOAK_ID = "kc-update-discount";
    private static final BigDecimal PAID_TOTAL = new BigDecimal("175.00");

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
    @Mock private OrderCreationTransactionalDelegate orderCreationTransactionalDelegate;
    @Mock private DiscountCampaignService discountCampaignService;

    private OrderManagementServiceImp service;
    private Jwt jwt;
    private ProductEntity product;

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
        service = new OrderManagementServiceImp(Mappers.getMapper(OrderMapper.class), cartService, stockService, paymentService,
                userRepository, orderRepository, productRepository, orderValidatorChain, eventPublisher, idempotencyKeyService,
                priceService, orderCancellationTransactionalDelegate, orderCreationTransactionalDelegate);

        jwt = mock(Jwt.class);
        lenient().when(jwt.getSubject()).thenReturn(KEYCLOAK_ID);
        lenient().when(idempotencyKeyService.generateKey(anyString(), anyList())).thenReturn("idem-update");
        lenient().when(orderRepository.save(any(OrderEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        product = ProductEntityBuilder.aValidProductBuilder().price(new BigDecimal("100.00")).build();
        lenient().when(productRepository.findAllByUuidIn(any())).thenReturn(List.of(product));
    }

    private static DiscountContext blackFridayTerms() {
        return DiscountContext.builder()
                .discountKey(BLACK_FRIDAY)
                .calculationType(DiscountCalculationType.PERCENTAGE)
                .percentage(new BigDecimal("20.00"))
                .minOrderAmount(new BigDecimal("150.00"))
                .build();
    }

    /** The reference order, PAID 175 EUR. {@code snapshot = false} simulates an order placed before the snapshot existed. */
    private OrderEntity paidBlackFridayOrder(final boolean snapshot) {
        final UserEntity user = UserEntityBuilder.aValidUserBuilder()
                .keycloakId(KEYCLOAK_ID)
                .bankCardEntity(BankCardEntityBuilder.aValidBankCard())
                .build();
        final OrderItemEntity item = OrderItemEntityBuilder.aValidOrderItemBuilder()
                .productEntity(product).quantity(2).unitPrice(product.getPrice()).subtotal(new BigDecimal("200.00")).build();
        final PaymentEntity payment = PaymentEntityBuilder.aValidPaymentBuilder()
                .status(PaymentAttemptStatus.SUCCESS)
                .transactionType(TransactionType.PAYMENT)
                .amount(new Money(PAID_TOTAL, CurrencyCode.EUR))
                .createdAt(LocalDateTime.now().minusMinutes(5))
                .build();
        final OrderEntity order = OrderEntityBuilder.aValidOrderBuilder()
                .userEntity(user)
                .status(OrderStatus.PAID)
                .discountKey(BLACK_FRIDAY)
                .discountTerms(snapshot ? DiscountTermsSnapshot.from(blackFridayTerms()) : null)
                .subtotalAmount(snapshot ? new BigDecimal("200.00") : null)
                .discountAmount(snapshot ? new BigDecimal("40.00") : null)
                .shippingCost(snapshot ? new BigDecimal("15.00") : null)
                .totalAmount(new Money(PAID_TOTAL, CurrencyCode.EUR))
                .orderItemEntities(new ArrayList<>(List.of(item)))
                .paymentAttempts(new ArrayList<>(List.of(payment)))
                .build();
        payment.setOrderEntity(order);
        when(orderRepository.lockByUuid(order.getUuid())).thenReturn(Optional.of(order));
        return order;
    }

    private OrderUpdateRequestDto update(final OrderEntity order, final int quantity, final ShippingType shippingType) {
        return OrderDtoFixtures.aValidUpdateRequestBuilder()
                .uuid(order.getUuid())
                .shippingType(shippingType)
                .shippingProvider(ShippingProvider.DHL)
                .shippingStreet("42 avenue Nouvelle")
                .itemUpdateRequestDtoList(List.of(OrderItemCreateRequestDto.builder()
                        .productUuid(product.getUuid()).quantity(quantity).build()))
                .build();
    }

    private static PaymentEntity accepted(final TransactionType type, final String amount) {
        return PaymentEntityBuilder.aValidPaymentBuilder()
                .status(PaymentAttemptStatus.SUCCESS).transactionType(type)
                .amount(new Money(new BigDecimal(amount), CurrencyCode.EUR)).createdAt(LocalDateTime.now()).build();
    }

    private static void assertBreakdown(final OrderEntity order, final String subtotal, final String discount,
                                        final String shipping, final String total) {
        assertThat(order.getSubtotalAmount()).isEqualByComparingTo(subtotal);
        assertThat(order.getDiscountAmount()).isEqualByComparingTo(discount);
        assertThat(order.getShippingCost()).isEqualByComparingTo(shipping);
        assertThat(order.getTotalAmount().getAmount()).isEqualByComparingTo(total);
    }

    @Test
    @DisplayName("BLACK_FRIDAY order, address changed only -> total unchanged, no complement, no refund")
    void addressOnly_keepsDiscount_noPaymentMovement() {
        final OrderEntity order = paidBlackFridayOrder(true);

        service.updateOrder(update(order, 2, ShippingType.STANDARD), jwt);

        assertBreakdown(order, "200.00", "40.00", "15.00", "175.00");
        assertThat(order.getShippingAddress().getStreet()).isEqualTo("42 avenue Nouvelle");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(paymentService, never()).processPayment(any(), any(), any(), anyString());
        verify(paymentService, never()).refund(any(), any(), any(), anyString());
        verify(stockService).commitStock(order.getUuid());
    }

    @Test
    @DisplayName("campaign disabled after the order -> the snapshot terms still apply, the campaign is not consulted")
    void campaignDisabledSinceOrder_discountKept() {
        final OrderEntity order = paidBlackFridayOrder(true);
        lenient().when(discountCampaignService.getActiveDiscountContext(BLACK_FRIDAY))
                .thenThrow(new DiscountTypeNotActiveException("BLACK_FRIDAY is disabled"));

        service.updateOrder(update(order, 2, ShippingType.STANDARD), jwt);

        assertBreakdown(order, "200.00", "40.00", "15.00", "175.00");
        verifyNoInteractions(discountCampaignService);
        verify(paymentService, never()).processPayment(any(), any(), any(), anyString());
    }

    @Test
    @DisplayName("quantity reduced below minOrderAmount -> discount drops to 0, refund of exactly 175 - 115 = 60")
    void belowMinimumAfterUpdate_discountZero_partialRefund() {
        final OrderEntity order = paidBlackFridayOrder(true);
        when(paymentService.refund(any(), any(), any(), anyString())).thenReturn(accepted(TransactionType.REFUND, "60.00"));

        service.updateOrder(update(order, 1, ShippingType.STANDARD), jwt);

        assertBreakdown(order, "100.00", "0.00", "15.00", "115.00");
        final ArgumentCaptor<Money> refunded = ArgumentCaptor.forClass(Money.class);
        verify(paymentService).refund(any(), any(), refunded.capture(), anyString());
        assertThat(refunded.getValue().getAmount()).isEqualByComparingTo("60.00");
        verify(paymentService, never()).processPayment(any(), any(), any(), anyString());
        // Terms stay frozen: raising the quantity again later re-grants the discount.
        assertThat(order.getDiscountTerms().getPercentage()).isEqualByComparingTo("20.00");
        assertThat(order.getDiscountKey()).isEqualTo(BLACK_FRIDAY);
    }

    @Test
    @DisplayName("STANDARD -> EXPRESS -> complement of exactly +10 EUR, discount kept")
    void standardToExpress_chargesExactlyTen() {
        final OrderEntity order = paidBlackFridayOrder(true);
        when(paymentService.processPayment(any(), any(), any(), anyString())).thenReturn(accepted(TransactionType.PAYMENT, "10.00"));

        service.updateOrder(update(order, 2, ShippingType.EXPRESS), jwt);

        assertBreakdown(order, "200.00", "40.00", "25.00", "185.00");
        final ArgumentCaptor<Money> charged = ArgumentCaptor.forClass(Money.class);
        verify(paymentService).processPayment(any(), any(), charged.capture(), anyString());
        assertThat(charged.getValue().getAmount()).isEqualByComparingTo("10.00");
        verify(paymentService, never()).refund(any(), any(), any(), anyString());
    }

    @Test
    @DisplayName("a new shipping option moves the estimated delivery dates, still counted from the original orderDate")
    void shippingChange_recomputesEstimatedDeliveryFromOrderDate() {
        final OrderEntity order = paidBlackFridayOrder(true);
        final LocalDateTime placedMondayBeforeCutoff = LocalDateTime.of(2026, 10, 12, 14, 0);
        order.setOrderDate(placedMondayBeforeCutoff);
        when(paymentService.processPayment(any(), any(), any(), anyString())).thenReturn(accepted(TransactionType.PAYMENT, "10.00"));

        service.updateOrder(update(order, 2, ShippingType.EXPRESS), jwt);

        // DHL EXPRESS, Monday 14:00 -> Tuesday.
        assertThat(order.getOrderDate()).isEqualTo(placedMondayBeforeCutoff);
        assertThat(order.getEstimatedDeliveryFrom()).isEqualTo(LocalDate.of(2026, 10, 13));
        assertThat(order.getEstimatedDeliveryTo()).isEqualTo(LocalDate.of(2026, 10, 13));
    }

    @Test
    @DisplayName("order without snapshot (placed before it existed), campaign still active -> current campaign terms, then frozen")
    void legacyOrder_campaignStillActive_usesCurrentCampaign() {
        final OrderEntity order = paidBlackFridayOrder(false);
        when(discountCampaignService.getActiveDiscountContext(BLACK_FRIDAY)).thenReturn(blackFridayTerms());

        service.updateOrder(update(order, 2, ShippingType.STANDARD), jwt);

        assertBreakdown(order, "200.00", "40.00", "15.00", "175.00");
        verify(paymentService, never()).processPayment(any(), any(), any(), anyString());
        assertThat(order.getDiscountTerms()).isNotNull();
        assertThat(order.getDiscountTerms().getCalculationType()).isEqualTo(DiscountCalculationType.PERCENTAGE);
    }

    @Test
    @DisplayName("order without snapshot, campaign no longer active -> falls back to NO_DISCOUNT (complement charged)")
    void legacyOrder_campaignInactive_fallsBackToNoDiscount() {
        final OrderEntity order = paidBlackFridayOrder(false);
        when(discountCampaignService.getActiveDiscountContext(BLACK_FRIDAY))
                .thenThrow(new DiscountTypeNotActiveException("BLACK_FRIDAY expired"));
        when(paymentService.processPayment(any(), any(), any(), anyString())).thenReturn(accepted(TransactionType.PAYMENT, "40.00"));

        service.updateOrder(update(order, 2, ShippingType.STANDARD), jwt);

        assertBreakdown(order, "200.00", "0.00", "15.00", "215.00");
        assertThat(order.getDiscountTerms().getCalculationType()).isEqualTo(DiscountCalculationType.NONE);
    }
}
