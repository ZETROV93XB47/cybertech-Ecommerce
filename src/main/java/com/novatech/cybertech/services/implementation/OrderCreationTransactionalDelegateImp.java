package com.novatech.cybertech.services.implementation;

import com.github.f4b6a3.uuid.UuidCreator;
import com.novatech.cybertech.dto.request.order.OrderItemPriceDto;
import com.novatech.cybertech.dto.request.order.OrderPlacingRequestDto;
import com.novatech.cybertech.dto.request.order.PriceCalculationRequestDto;
import com.novatech.cybertech.dto.response.order.PriceCalculationResultDto;
import com.novatech.cybertech.entities.CartEntity;
import com.novatech.cybertech.entities.CartItemEntity;
import com.novatech.cybertech.entities.OrderEntity;
import com.novatech.cybertech.entities.OrderItemEntity;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.entities.enums.OrderStatus;
import com.novatech.cybertech.entities.valueObjects.Address;
import com.novatech.cybertech.entities.valueObjects.CurrencyCode;
import com.novatech.cybertech.entities.valueObjects.Money;
import com.novatech.cybertech.exceptions.CartNotFoundException;
import com.novatech.cybertech.exceptions.UserNotFoundException;
import com.novatech.cybertech.repositories.OrderRepository;
import com.novatech.cybertech.repositories.UserRepository;
import com.novatech.cybertech.services.core.OrderCreationTransactionalDelegate;
import com.novatech.cybertech.services.core.OrderPriceCalculationService;
import com.novatech.cybertech.services.core.StockService;
import com.novatech.cybertech.validator.core.OrderValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Transactional inner half of the placeOrder design. See
 * {@link OrderCreationTransactionalDelegate} for the full "why a separate bean" rationale.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderCreationTransactionalDelegateImp implements OrderCreationTransactionalDelegate {

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final StockService stockService;
    private final OrderValidator orderValidatorChain;
    private final OrderPriceCalculationService orderPriceCalculationService;

    @Override
    @Transactional
    public OrderEntity createAndReserveStock(final OrderPlacingRequestDto req, final String keycloakId) {

        final UserEntity user = userRepository.findByKeycloakId(keycloakId).orElseThrow(() -> new UserNotFoundException("User not found"));

        final CartEntity cart = user.getCartEntity();
        if (cart == null || cart.getCartItems() == null || cart.getCartItems().isEmpty()) {
            throw new CartNotFoundException("Cannot place order: Cart is empty");
        }
        final List<CartItemEntity> cartItems = cart.getCartItems();

        final Map<UUID, Integer> quantities = cartItems.stream()
                .collect(Collectors.toMap(item -> item.getProductEntity().getUuid(), CartItemEntity::getQuantity));

        OrderManagementServiceImp.validateUserBeforeProcessingPayment(orderValidatorChain, user);

        final UUID orderUuid = UuidCreator.getTimeOrderedEpoch();

        // Price is read live from the product here — the cart itself carries no frozen price
        // (see CartItemEntity) — this is the moment the price actually gets locked in for the
        // order, at time of purchase rather than at time of add-to-cart.
        final List<OrderItemPriceDto> priceDtos = cartItems.stream()
                .map(item -> OrderItemPriceDto.builder()
                        .productUuid(item.getProductEntity().getUuid())
                        .unitPrice(item.getProductEntity().getPrice())
                        .quantity(item.getQuantity())
                        .build())
                .toList();

        final PriceCalculationRequestDto priceRequest = PriceCalculationRequestDto.builder()
                .items(priceDtos)
                .discountType(req.getDiscountType())
                .currencyCode(CurrencyCode.fromCode("EUR"))
                .shippingProvider(req.getShippingProvider())
                .shippingType(req.getShippingType())
                .build();

        final PriceCalculationResultDto priceResult = orderPriceCalculationService.calculate(priceRequest);
        final Money totalMoney = priceResult.asFinalMoney();
        final BigDecimal totalAmount = totalMoney.getAmount();

        final List<OrderItemEntity> orderItems = cartItems.stream()
                .map(item -> OrderItemEntity.builder()
                        .unitPrice(item.getProductEntity().getPrice())
                        .quantity(item.getQuantity())
                        .subtotal(item.getProductEntity().getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                        .productEntity(item.getProductEntity())
                        .build())
                .collect(Collectors.toList());

        final OrderEntity order = OrderEntity.builder()
                .uuid(orderUuid)
                .userEntity(user)
                .orderItemEntities(orderItems)
                .totalAmount(Money.of(totalAmount))
                .discountType(req.getDiscountType())
                .status(OrderStatus.CREATED)
                .orderDate(LocalDateTime.now())
                .shippingProvider(req.getShippingProvider())
                .shippingType(req.getShippingType())
                .shippingAddress(Address.builder()
                        .street(req.getShippingStreet())
                        .city(req.getShippingCity())
                        .zipCode(req.getShippingZipCode())
                        .country(req.getShippingCountry())
                        .build())
                .build();

        // Lier les items à la commande pour que la clé étrangère orderId soit peuplée lors du save
        orderItems.forEach(item -> item.setOrderEntity(order));

        order.setStatus(OrderStatus.AWAITING_PAYMENT);
        final OrderEntity savedOrder = orderRepository.save(order);

        // Reserve stock (throws -> this transaction rolls back, nothing committed).
        stockService.reserveStock(orderUuid, quantities);

        return savedOrder;
    }
}
