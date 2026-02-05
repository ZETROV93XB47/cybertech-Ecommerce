package com.novatech.cybertech.services.implementation;


import com.github.f4b6a3.uuid.UuidCreator;
import com.novatech.cybertech.dispatcher.ShippingDispatcher;
import com.novatech.cybertech.dto.data.OrderEventDto;
import com.novatech.cybertech.dto.data.OrderValidationDto;
import com.novatech.cybertech.dto.data.ShippingContext;
import com.novatech.cybertech.dto.data.UserContactDto;
import com.novatech.cybertech.dto.request.order.OrderPlacingRequestDto;
import com.novatech.cybertech.dto.request.order.OrderUpdateRequestDto;
import com.novatech.cybertech.dto.request.orderItem.OrderItemCreateRequestDto;
import com.novatech.cybertech.dto.response.order.OrderResponseDto;
import com.novatech.cybertech.entities.*;
import com.novatech.cybertech.entities.enums.OrderStatus;
import com.novatech.cybertech.entities.enums.PaymentAttemptStatus;
import com.novatech.cybertech.entities.enums.ShippingProvider;
import com.novatech.cybertech.entities.enums.ShippingType;
import com.novatech.cybertech.entities.valueObjects.Address;
import com.novatech.cybertech.entities.valueObjects.Money;
import com.novatech.cybertech.events.OrderCreatedEvent;
import com.novatech.cybertech.events.OrderPaidEvent;
import com.novatech.cybertech.exceptions.*;
import com.novatech.cybertech.mappers.entity.OrderMapper;
import com.novatech.cybertech.repositories.OrderRepository;
import com.novatech.cybertech.repositories.ProductRepository;
import com.novatech.cybertech.repositories.UserRepository;
import com.novatech.cybertech.services.core.*;
import com.novatech.cybertech.validator.core.OrderValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.novatech.cybertech.entities.enums.DiscountType.NO_DISCOUNT;
import static com.novatech.cybertech.entities.enums.OrderStatus.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderManagementServiceImp implements OrderManagementService {

    private final OrderMapper orderMapper;

    private final CartService cartService;
    private final StockService stockService;
    private final PaymentService paymentService;

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    private final OrderValidator orderValidatorChain;
    private final ShippingDispatcher shippingDispatcher;
    private final ApplicationEventPublisher eventPublisher;

    private final IdempotencyKeyServiceGenerator idempotencyKeyServiceGenerator;


    //TODO: refactor this method to make it callable only by an admin or separate this crud method in another service, a crud service for instance
    @Transactional(readOnly = true)
    public Collection<OrderResponseDto> getAll() {
        return orderRepository.findAll().stream().map(orderMapper::mapFromEntityToResponseDto).toList();
    }

    //TODO: refactor this method to make it callable only by an admin or separate this crud method in another service, a crud service for instance
    @Transactional(readOnly = true)
    public OrderResponseDto getByUUID(UUID uuid) {
        return orderMapper.mapFromEntityToResponseDto(orderRepository.findByUuid(uuid).orElseThrow(() -> new OrderNotFoundException("No product with the UUID : " + uuid + " found")));
    }

    //TODO: refactor this method to make it callable only by an admin or separate this crud method in another service, a crud service for instance
    @Transactional(readOnly = true)
    public Collection<OrderResponseDto> getByUUIDs(Collection<UUID> uuids) {
        return orderRepository.findAllByUuidIn(uuids).stream().map(orderMapper::mapFromEntityToResponseDto).toList();
    }

    //TODO: refactor this method to make it callable only by an admin or separate this crud method in another service, a crud service for instance
    @Override
    @Transactional
    public void deleteByUUID(final UUID uuid, final Jwt jwt) {

        final String keycloakId = jwt.getSubject();
        final OrderEntity orderEntity = orderRepository.findByUuid(uuid).orElseThrow(() -> new OrderNotFoundException("Order not found"));

        if (isCurrentUserOrderInitiator(orderEntity, keycloakId)) {
            if (isInDeletableState(orderEntity)) orderRepository.deleteByUuid(uuid);
            else {
                log.info("Order is not in Deletable state, order current state : {}", orderEntity.getStatus().name());
                throw new CannotCancelOrderException("Order is not in Deletable state, order current state : " + orderEntity.getStatus().name());
            }
        }
        throw new OrderDoesntBelongsToUserException("Order not found for this user account");
    }

    @Override
    @Transactional
    public OrderResponseDto updateOrder(final OrderUpdateRequestDto dto, final Jwt jwt) {

        final String keycloakId = jwt.getSubject();

        final OrderEntity order = orderRepository.findByUuid(dto.getUuid())
                .orElseThrow(() -> new OrderNotFoundException("Order not found"));

        if (!isCurrentUserOrderInitiator(order, keycloakId)) {
            throw new OrderDoesntBelongsToUserException("Order not found for this user account");
        }
        if (isOrderAlreadyShipped(order)) {
            throw new FailedUpdatingOrder("Order already shipped, cannot update");
        }

        // 0) Tu overwrites les items : libère l'ancienne réservation (si existante)
        //    (safe même si rien n'était réservé)
        stockService.releaseStock(order.getUuid());

        // 1) Charger les produits + quantités demandées
        final List<ProductEntity> products = getAllProductsFromRequest(dto.getItemUpdateRequestDtoList());
        final Map<UUID, Integer> quantities = dto.getItemUpdateRequestDtoList().stream().collect(Collectors.toMap(OrderItemCreateRequestDto::getProductUuid, OrderItemCreateRequestDto::getQuantity));

        // 2) Validation
        validateOrderBeforeProcessingPayment(order.getUserEntity());

        // 3) Calculer le total
        final BigDecimal amount = processOrderTotalPrice(dto.getItemUpdateRequestDtoList(), products);
        final Money total = Money.of(amount);

        // 4) Update shipping + address + total + statut
        order.setShippingType(dto.getShippingType());
        order.setShippingProvider(dto.getShippingProvider());
        order.setShippingAddress(Address.builder()
                .street(dto.getShippingStreet())
                .city(dto.getShippingCity())
                .zipCode(dto.getShippingZipCode())
                .country(dto.getShippingCountry())
                .build());
        order.setTotalAmount(total);
        order.setStatus(OrderStatus.AWAITING_PAYMENT);

        // 5) Overwrite des items (selon ton choix)
        //    Ici, je suppose que tu sais reconstruire la liste OrderItemEntity depuis dto + products
        final List<OrderItemEntity> newItems = mapToOrderItems(quantities, products, order);
        order.getOrderItemEntities().clear();
        order.getOrderItemEntities().addAll(newItems);

        orderRepository.save(order);

        // 6) Réserver le stock pour les nouveaux items
        stockService.reserveStock(order.getUuid(), quantities);

        // 7) Paiement attempt (idempotent)
        final String idemKey = (dto.getIdempotencyKey() != null && !dto.getIdempotencyKey().isBlank())
                ? dto.getIdempotencyKey()
                : (order.getUuid() + ":update:" + System.currentTimeMillis()); // fallback back

        final PaymentAttemptEntity attempt = paymentService.processPayment(
                order,
                dto.getPaymentType(),
                total,
                idemKey
        );

        log.info("payment :: {}", attempt);

        // 8) Statut commande + stock selon résultat
        return switch (attempt.getStatus()) {

            case SUCCESS -> {
                stockService.commitStock(order.getUuid());

                order.setStatus(OrderStatus.PAID);
                final OrderEntity saved = orderRepository.save(order);

                // Déclenche expédition via listener AFTER_COMMIT
                eventPublisher.publishEvent(new OrderPaidEvent(saved.getUuid()));

                yield orderMapper.mapFromEntityToResponseDto(saved);
            }

            case FAILED, CANCELED -> {
                // On libère explicitement ici pour ne pas dépendre du TTL.
                stockService.releaseStock(order.getUuid());

                order.setStatus(OrderStatus.PAYMENT_FAILED);
                final OrderEntity saved = orderRepository.save(order);

                yield orderMapper.mapFromEntityToResponseDto(saved);
            }

            case CREATED, PROCESSING -> {
                // sans 3DS, normalement rare, mais propre
                order.setStatus(OrderStatus.AWAITING_PAYMENT);
                final OrderEntity saved = orderRepository.save(order);
                yield orderMapper.mapFromEntityToResponseDto(saved);
            }
        };
    }




    @Override
    @Transactional
    public OrderResponseDto placeOrder(final OrderPlacingRequestDto req, final Jwt jwt) {

        final String keycloakId = jwt.getSubject();
        final UserEntity user = userRepository.findByKeycloakId(keycloakId).orElseThrow(() -> new UserNotFoundException("User not found"));

        // 1) Récupération panier
        final CartEntity cart = user.getCartEntity();

        if (cart == null || cart.getCartItems() == null || cart.getCartItems().isEmpty()) {
            throw new CartNotFoundException("Cannot place order: Cart is empty");
        }

        final List<CartItemEntity> cartItems = cart.getCartItems();

        // 2) Quantités pour stock
        final Map<UUID, Integer> quantities = cartItems.stream()
                .collect(Collectors.toMap(
                        item -> item.getProductEntity().getUuid(),
                        CartItemEntity::getQuantity
                ));

        validateOrderBeforeProcessingPayment(user);

        // 3) UUID commande (v7/ordered)
        final UUID orderUuid = UuidCreator.getTimeOrderedEpoch();

        // 4) Total
        final BigDecimal totalAmount = cartItems.stream()
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        final Money totalMoney = Money.of(totalAmount);

        // 5) Items commande
        final List<OrderItemEntity> orderItems = cartItems.stream()
                .map(item -> OrderItemEntity.builder()
                        .unitPrice(item.getUnitPrice())
                        .quantity(item.getQuantity())
                        .subtotal(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                        .productEntity(item.getProductEntity())
                        // IMPORTANT: si OrderItemEntity a un champ orderEntity, set-le ici
                        // .orderEntity(order)
                        .build())
                .collect(Collectors.toList());

        // 6) Créer + sauver la commande AVANT paiement (toujours persistée)
        final OrderEntity order = initOrderEntity(
                orderUuid,
                req.getShippingCity(),
                req.getShippingStreet(),
                req.getShippingZipCode(),
                req.getShippingCountry(),
                req.getShippingType(),
                req.getShippingProvider(),
                totalAmount,
                orderItems,
                user
        );

        // FIX: Lier les items à la commande pour que la clé étrangère orderId soit peuplée lors du save
        orderItems.forEach(item -> item.setOrderEntity(order));

        order.setStatus(OrderStatus.AWAITING_PAYMENT);
        final OrderEntity savedOrder = orderRepository.save(order);

        // 7) Réserver stock (si ça throw -> transaction rollback)
        stockService.reserveStock(orderUuid, quantities);

        // 8) Paiement attempt (idempotent)
        // Reco: ajoute req.getIdempotencyKey() côté DTO.
        final String idempotencyKey = (req.getIdempotencyKey() != null && !req.getIdempotencyKey().isBlank()) ? req.getIdempotencyKey() : idempotencyKeyServiceGenerator.generateKey(orderUuid.toString(), orderItems.stream().map(item -> item.getProductEntity().getUuid().toString()).collect(Collectors.toList()));
        final PaymentAttemptEntity attempt;

        try {
            attempt = paymentService.processPayment(
                    savedOrder,
                    req.getPaymentType(),
                    totalMoney,
                    idempotencyKey
            );
        }
        catch (PaymentAlreadyCompletedForThisOrderException e) {
            return orderMapper.mapFromEntityToResponseDto(savedOrder);
        }


        log.info("payment :: {}", attempt);


        // 9) Vider le panier après la tentative (commande existante + stock réservé)
        //    Si tu préfères ne vider qu'après SUCCESS, déplace-le dans le case SUCCESS.
        cartService.clearCart(jwt);

        // 10) Statuts + stock + events
        return switch (attempt.getStatus()) {

            case SUCCESS -> {
                stockService.commitStock(orderUuid);

                savedOrder.setStatus(OrderStatus.PAID);
                final OrderEntity paidOrder = orderRepository.save(savedOrder);

                // Event création (corrige ton paymentStatus: plus de SUCCESS en dur)
                sendOrderCreationEvent(paidOrder, user, totalAmount, attempt.getStatus());

                // Expédition décorrélée : listener AFTER_COMMIT déclenche shippingDispatcher.dispatch(...)
                eventPublisher.publishEvent(new OrderPaidEvent(paidOrder.getUuid()));

                yield orderMapper.mapFromEntityToResponseDto(paidOrder);
            }

            case FAILED, CANCELED -> {
                // Même si tu as TTL Redis, release immédiat = stock dispo tout de suite.
                stockService.releaseStock(orderUuid);

                savedOrder.setStatus(OrderStatus.PAYMENT_FAILED);
                final OrderEntity failedOrder = orderRepository.save(savedOrder);

                sendOrderCreationEvent(failedOrder, user, totalAmount, attempt.getStatus());

                yield orderMapper.mapFromEntityToResponseDto(failedOrder);
            }

            case CREATED, PROCESSING -> {
                // sans 3DS tu ne devrais pas rester là, mais on est clean
                savedOrder.setStatus(OrderStatus.AWAITING_PAYMENT);
                final OrderEntity awaiting = orderRepository.save(savedOrder);

                sendOrderCreationEvent(awaiting, user, totalAmount, attempt.getStatus());

                yield orderMapper.mapFromEntityToResponseDto(awaiting);
            }
        };
    }


    @Override
    @Transactional
    public OrderResponseDto cancelOrder(final UUID orderUUID, final Jwt jwt) {

        log.info("Order UUD : {}", orderUUID);

        final OrderEntity orderEntity = orderRepository.findByUuid(orderUUID).orElseThrow(() -> new OrderNotFoundException("Order with UUID " + orderUUID + " not found"));

        if (isOrderAlreadyShipped(orderEntity)) {//Il faudra un autre endpoint pour annuler la commande une fois renvoyée
            final String keycloakId = jwt.getSubject();
            if (isCurrentUserOrderInitiator(orderEntity, keycloakId)) {
                orderEntity.setStatus(OrderStatus.CANCELED);
                return orderMapper.mapFromEntityToResponseDto(orderRepository.save(orderEntity));
            } else {
                log.info("User tried to cancel and order not linked to his account");
                throw new OrderDoesntBelongsToUserException("Order with UUID " + orderUUID + " not found for this user account");
            }
        } else {
            log.info("User tried to cancel an order in a status over delivered status");
            throw new CannotCancelOrderException("Order is already shipped and can't be cancelled, please consider initiating Return process");
        }
    }


    /*

    private OrderResponseDto onPaymentSuccess(
            final PaymentAttemptEntity attempt,
            final UUID orderUUID,
            final OrderEntity order,
            final UserEntity userEntity,
            final BigDecimal amount
    ) {
        stockService.commitStock(orderUUID);

        order.setStatus(OrderStatus.PAID);
        OrderEntity saved = orderRepository.save(order);

        sendOrderCreationEvent(saved, userEntity, amount, attempt.getStatus());

        // plus de dispatch direct ici
        eventPublisher.publishEvent(new OrderPaidEvent(saved.getUuid()));

        return orderMapper.mapFromEntityToResponseDto(saved);
    }


    private OrderResponseDto onPaymentFailure(final UUID orderUUID, final OrderEntity order) {
        stockService.releaseStock(orderUUID);

        order.setStatus(OrderStatus.PAYMENT_FAILED);
        OrderEntity saved = orderRepository.save(order);

        // paymentStatus dans l’event doit refléter FAILED, pas SUCCESS
        sendOrderCreationEvent(saved, order.getUserEntity(), saved.getTotalAmount().getAmount(), PaymentAttemptStatus.FAILED);

        return orderMapper.mapFromEntityToResponseDto(saved);
    }

     */


    private void validateOrderBeforeProcessingPayment(final UserEntity userEntity) {
        final OrderValidationDto orderValidationDto = OrderValidationDto.builder()
                .isUserActive(userEntity.getIsActive())
                .userDefaultBankCard(Optional.ofNullable(userEntity.getBankCardEntity()).orElseThrow(() -> new NoDefaultBankCartSetException("No bank card set, please, add a bank card and retry ...")))
                .build();

        orderValidatorChain.validate(orderValidationDto);
    }


    private static OrderEntity initOrderEntity(final UUID orderUuid,
                                               final String shippingCity,
                                               final String shippingStreet,
                                               final String shippingZipCode,
                                               final String shippingCountry,
                                               final ShippingType shippingType,
                                               final ShippingProvider shippingProvider,
                                               final BigDecimal totalPrice,
                                               final List<OrderItemEntity> orderItemEntities,
                                               final UserEntity user) {
        return OrderEntity.builder()
                .uuid(orderUuid)
                .userEntity(user)
                .orderItemEntities(orderItemEntities)
                .totalAmount(Money.of(totalPrice))
                .discountType(NO_DISCOUNT)
                .status(CREATED)
                .orderDate(LocalDateTime.now())
                .shippingProvider(shippingProvider)
                .shippingType(shippingType)
                .shippingAddress(Address.builder()
                        .street(shippingStreet)
                        .city(shippingCity)
                        .zipCode(shippingZipCode)
                        .country(shippingCountry)
                        .build())
                .build();
    }


    private void sendOrderShippingEvent(final ShippingType shippingType, final ShippingProvider shippingProvider, final UserEntity user, final OrderEntity savedOrder) {
        final ShippingContext shippingContext = ShippingContext.builder()
                .user(user)
                .packageId(savedOrder.getUuid().toString())
                .payload(savedOrder)
                .shippingType(shippingType)
                .shippingProvider(shippingProvider)
                .build();

        shippingDispatcher.dispatch(shippingContext);
    }


    private void sendOrderCreationEvent(
            final OrderEntity orderEntity,
            final UserEntity user,
            final BigDecimal totalPrice,
            final PaymentAttemptStatus paymentAttemptStatus
    ) {
        OrderEventDto orderEventDto = OrderEventDto.builder()
                .orderUuid(orderEntity.getUuid())
                .orderStatus(orderEntity.getStatus())
                .paymentAttemptStatus(paymentAttemptStatus)
                .totalAmount(totalPrice)
                .userContactDto(UserContactDto.builder()
                        .defaultCommunicationChanel(user.getFavoriteCommunicationChanel())
                        .email(user.getEmail())
                        .name(user.getFirstName())
                        .phoneNumber(user.getPhoneNumber())
                        .build())
                .build();

        eventPublisher.publishEvent(new OrderCreatedEvent(this, orderEventDto));
    }


    private BigDecimal processOrderTotalPrice(final List<OrderItemCreateRequestDto> orderItemCreateRequestDto, final List<ProductEntity> productEntities) {

        final Map<UUID, ProductEntity> productsByUuid = productEntities.stream().collect(Collectors.toMap(ProductEntity::getUuid, product -> product));

        return orderItemCreateRequestDto.stream()
                .map(orderItem -> {
                    ProductEntity product = productsByUuid.get(orderItem.getProductUuid());
                    if (product == null) {
                        log.warn("Product with UUID {} from order request not found in fetched products.", orderItem.getProductUuid());
                        return BigDecimal.ZERO;
                    }

                    return product.getPrice().multiply(BigDecimal.valueOf(orderItem.getQuantity()));
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }


    private List<ProductEntity> getAllProductsFromRequest(final List<OrderItemCreateRequestDto> orderItemCreateRequestDto) {
        return productRepository.findAllByUuidIn(orderItemCreateRequestDto.stream().map(OrderItemCreateRequestDto::getProductUuid).toList());
    }

    private static boolean isOrderAlreadyShipped(OrderEntity orderEntity) {
        return orderEntity.getStatus().getCode() < SHIPPED.getCode();
    }

    private static boolean isCurrentUserOrderInitiator(OrderEntity orderEntity, String keycloakId) {
        return orderEntity.getUserEntity().getKeycloakId().equals(keycloakId);
    }

    private static boolean isInDeletableState(final OrderEntity order) {
        final Set<OrderStatus> deletableStates = Set.of(AWAITING_PAYMENT, DELIVERED, RETURNED, CANCELED, REFUNDED);
        return deletableStates.contains(order.getStatus());
    }

    private List<OrderItemEntity> mapToOrderItems(Map<UUID, Integer> quantities, List<ProductEntity> products, OrderEntity order) {
        return products.stream()
                .map(product -> OrderItemEntity.builder()
                        .unitPrice(product.getPrice())
                        .quantity(quantities.get(product.getUuid()))
                        .subtotal(product.getPrice().multiply(BigDecimal.valueOf(quantities.get(product.getUuid()))))
                        .orderEntity(order)
                        .productEntity(product)
                        .build())
                .collect(Collectors.toUnmodifiableList());
    }
}
