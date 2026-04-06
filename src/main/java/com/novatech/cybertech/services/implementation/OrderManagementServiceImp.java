package com.novatech.cybertech.services.implementation;


import com.github.f4b6a3.uuid.UuidCreator;
import com.novatech.cybertech.dto.data.OrderEventDto;
import com.novatech.cybertech.dto.data.OrderValidationDto;
import com.novatech.cybertech.dto.data.UserContactDto;
import com.novatech.cybertech.dto.request.order.OrderPlacingRequestDto;
import com.novatech.cybertech.dto.request.order.OrderUpdateRequestDto;
import com.novatech.cybertech.dto.request.orderItem.OrderItemCreateRequestDto;
import com.novatech.cybertech.dto.response.order.OrderResponseDto;
import com.novatech.cybertech.entities.*;
import com.novatech.cybertech.entities.enums.*;
import com.novatech.cybertech.entities.valueObjects.Address;
import com.novatech.cybertech.entities.valueObjects.Money;
import com.novatech.cybertech.events.OrderCreatedEvent;
import com.novatech.cybertech.events.OrderUpdatedEvent;
import com.novatech.cybertech.exceptions.*;
import com.novatech.cybertech.mappers.entity.OrderMapper;
import com.novatech.cybertech.repositories.OrderRepository;
import com.novatech.cybertech.repositories.ProductRepository;
import com.novatech.cybertech.repositories.UserRepository;
import com.novatech.cybertech.services.core.OrderManagementService;
import com.novatech.cybertech.services.core.PaymentService;
import com.novatech.cybertech.services.core.StockService;
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

    //private final CartService cartService;
    private final StockService stockService;
    private final PaymentService paymentService;

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    private final OrderValidator orderValidatorChain;
    private final ApplicationEventPublisher eventPublisher;


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

        log.info("in Update Order : {}", dto);

        final String keycloakId = jwt.getSubject();

        final OrderEntity order = orderRepository.findByUuid(dto.getUuid()).orElseThrow(() -> new OrderNotFoundException("Order not found"));

        log.info("Order found : {}", order);

        if (!isCurrentUserOrderInitiator(order, keycloakId)) {
            throw new OrderDoesntBelongsToUserException("Order not found for this user account");
        }

        if (isOrderAlreadyShipped(order)) {
            log.info("Order already shipped, cannot update");
            throw new OrderAlreadyShippedException("Order already shipped, cannot update");
        }

        // 0) Tu overwrites les items : libère l'ancienne réservation (si existante)
        //    (safe même si rien n'était réservé)
        stockService.releaseStock(order.getUuid());

        // 1) Charger les produits + quantités demandées
        final List<ProductEntity> products = getAllProductsFromRequest(dto.getItemUpdateRequestDtoList());
        final Map<UUID, Integer> quantities = dto.getItemUpdateRequestDtoList().stream().collect(Collectors.toMap(OrderItemCreateRequestDto::getProductUuid, OrderItemCreateRequestDto::getQuantity));

        // 2) Validation
        validateUserBeforeProcessingPayment(order.getUserEntity());//TODO: is it really necessary to make this check here ? maybe make it before launching the order placing process

        // 3) Calculer le total
        final BigDecimal amount = processOrderTotalPrice(dto.getItemUpdateRequestDtoList(), products);
        final Money total = Money.of(amount);

        // Calcul du montant déjà payé (Paiements - Remboursements)
        BigDecimal paidAmount = order.getPaymentAttempts().stream()
                .filter(p -> p.getStatus() == PaymentAttemptStatus.SUCCESS)
                .map(p -> p.getTransactionType() == TransactionType.REFUND ? p.getAmount().getAmount().negate() : p.getAmount().getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal difference = total.getAmount().subtract(paidAmount);
        log.info("Update Order: New Total: {}, Paid: {}, Difference: {}", total.getAmount(), paidAmount, difference);

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

        // 5) Overwrite des items (selon ton choix)
        //    Ici, je suppose que tu sais reconstruire la liste OrderItemEntity depuis dto + products
        final List<OrderItemEntity> newItems = mapToOrderItems(quantities, products, order);
        order.getOrderItemEntities().clear();
        order.getOrderItemEntities().addAll(newItems);

        orderRepository.save(order);

        // 6) Réserver le stock pour les nouveaux items
        stockService.reserveStock(order.getUuid(), quantities);

        // 7) Paiement attempt (idempotent)
        //final String idemKey = generateIdempotencyKey(order.getUuid(), "update");

        if (difference.compareTo(BigDecimal.ZERO) == 0) {
            // Cas 3 : Pas de différence de prix
            // On valide juste le stock et on s'assure que le statut est PAID
            stockService.commitStock(order.getUuid());
            if (order.getStatus() != OrderStatus.PAID) {
                order.setStatus(OrderStatus.PAID);
            }
            final OrderEntity saved = orderRepository.save(order);
            return orderMapper.mapFromEntityToResponseDto(saved);
        }


        final PaymentEntity attempt = handlePaymentUpdate(order, difference, dto.getPaymentType(), order.getPaymentAttempts().getLast().getIdempotencyKey());

        sendOrderUpdatedEvent(order, order.getUserEntity(), total.getAmount(), attempt.getStatus());

        return orderMapper.mapFromEntityToResponseDto(order);
    }

    @Override
    @Transactional
    public OrderResponseDto retryPayment(final UUID orderUuid, final Jwt jwt) {
        final String keycloakId = jwt.getSubject();
        final OrderEntity order = orderRepository.findByUuid(orderUuid).orElseThrow(() -> new OrderNotFoundException("Order not found"));

        if (!isCurrentUserOrderInitiator(order, keycloakId)) {
            throw new OrderDoesntBelongsToUserException("Order not found for this user account");
        }

        if (!isOrderInRetryablePaymentStatus(order)) {
            throw new FailedRetryingPayment("Cannot retry payment for order in status: " + order.getStatus() + ". Order must be in PAYMENT_FAILED state.");
        }

        // 1. Vérifier et Réserver le stock (car il a été libéré lors de l'échec précédent)
        final Map<UUID, Integer> quantities = order.getOrderItemEntities().stream()
                .collect(Collectors.toMap(
                        item -> item.getProductEntity().getUuid(),
                        OrderItemEntity::getQuantity
                ));

        // Lève NotEnoughStockException si le stock n'est plus disponible
        stockService.reserveStock(order.getUuid(), quantities);

        // 2. Récupérer le type de paiement de la dernière tentative
        PaymentType paymentType = order.getPaymentAttempts().stream()
                .max(Comparator.comparing(BaseEntity::getCreatedAt))
                .map(PaymentEntity::getPaymentType)
                .orElseThrow(() -> new NoPreviousPaymentAttemptException("No previous payment attempt found for failed order"));

        // 3. Tenter le paiement
        final String idempotencyKey = generateIdempotencyKey(order.getUuid(), "retry");
        final PaymentEntity attempt = paymentService.processPayment(
                order,
                paymentType,
                order.getTotalAmount(),
                idempotencyKey
        );

        log.info("Retry payment attempt :: {}", attempt);

        // 4. Gérer le résultat (Commit stock si succès, Release si échec)
        sendOrderCreationEvent(order, order.getUserEntity(), order.getTotalAmount().getAmount(), attempt.getStatus());

        return orderMapper.mapFromEntityToResponseDto(order);
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

        validateUserBeforeProcessingPayment(user);//TODO: is it really necessary to make this check here ? maybe make it before launching the order placing process

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
        final String idempotencyKey = generateIdempotencyKey(orderUuid, "place");
        final PaymentEntity attempt;

        //TODO: je pense que ça n'a pas de sens de faire un try catch ici parce qu'une commande nouvellement passée n'a pas lieu d'aboutir sur un paiement déjà effectué
        attempt = paymentService.processPayment(
                savedOrder,
                req.getPaymentType(),
                totalMoney,
                idempotencyKey
        );

        log.info("payment :: {}", attempt);

        // 9) Vider le panier après la tentative (commande existante + stock réservé)
        //    Si tu préfères ne vider qu'après SUCCESS, déplace-le dans le case SUCCESS.
        // 10) Statuts + stock + events

        sendOrderCreationEvent(savedOrder, user, totalAmount, attempt.getStatus());//TODO: vérifier cette partie plus tard si saved order est good

        return orderMapper.mapFromEntityToResponseDto(savedOrder);
    }


    @Override
    @Transactional
    public OrderResponseDto cancelOrder(final UUID orderUUID, final Jwt jwt) {

        log.info("Order UUD : {}", orderUUID);

        final OrderEntity orderEntity = orderRepository.findByUuid(orderUUID).orElseThrow(() -> new OrderNotFoundException("Order with UUID " + orderUUID + " not found"));

        if (!isOrderAlreadyShipped(orderEntity)) {//Il faudra un autre endpoint pour annuler la commande une fois renvoyée
            final String keycloakId = jwt.getSubject();
            if (isCurrentUserOrderInitiator(orderEntity, keycloakId)) {
                orderEntity.setStatus(OrderStatus.CANCELED);


                orderEntity.getPaymentAttempts().stream()
                        .filter(p -> p.getStatus() == PaymentAttemptStatus.SUCCESS)
                        .filter(p -> p.getTransactionType() == TransactionType.PAYMENT)
                        .forEach(paymentAttemptEntity -> paymentService.refund(orderEntity, paymentAttemptEntity.getPaymentType(), paymentAttemptEntity.getAmount(), paymentAttemptEntity.getIdempotencyKey()));


                //paymentService.refund(orderEntity, orderEntity.getPaymentAttempts().getLast().getPaymentType(), orderEntity.getTotalAmount(), generateIdempotencyKey(orderUUID, "cancel"));
                //TODO: check if it's better to user the calculated amountToRefund or the order totalAmount


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


    private void validateUserBeforeProcessingPayment(final UserEntity userEntity) {
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
                .shippingProvider(orderEntity.getShippingProvider())
                .shippingType(orderEntity.getShippingType())
                .userContactDto(UserContactDto.builder()
                        .defaultCommunicationChanel(user.getFavoriteCommunicationChanel())
                        .email(user.getEmail())
                        .name(user.getFirstName())
                        .phoneNumber(user.getPhoneNumber())
                        .build())
                .build();

        eventPublisher.publishEvent(new OrderCreatedEvent(this, orderEventDto));
    }

    private void sendOrderUpdatedEvent(final OrderEntity orderEntity, final UserEntity user, final BigDecimal totalPrice, final PaymentAttemptStatus paymentAttemptStatus) {
        OrderEventDto orderEventDto = OrderEventDto.builder()
                .orderUuid(orderEntity.getUuid())
                .orderStatus(orderEntity.getStatus())
                .paymentAttemptStatus(paymentAttemptStatus)
                .totalAmount(totalPrice)
                .shippingProvider(orderEntity.getShippingProvider())
                .shippingType(orderEntity.getShippingType())
                .userContactDto(UserContactDto.builder()
                        .defaultCommunicationChanel(user.getFavoriteCommunicationChanel())
                        .email(user.getEmail())
                        .name(user.getFirstName())
                        .phoneNumber(user.getPhoneNumber())
                        .build())
                .build();

        eventPublisher.publishEvent(new OrderUpdatedEvent(this, orderEventDto));

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
        return orderEntity.getStatus().getCode() >= SHIPPED.getCode();
    }

    private static boolean isCurrentUserOrderInitiator(OrderEntity orderEntity, String keycloakId) {
        return orderEntity.getUserEntity().getKeycloakId().equals(keycloakId);
    }

    private static boolean isOrderInRetryablePaymentStatus(OrderEntity orderEntity) {
        return orderEntity.getStatus() == OrderStatus.PAYMENT_FAILED || orderEntity.getStatus() == OrderStatus.AWAITING_PAYMENT || orderEntity.getStatus() == CREATED;
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

    private PaymentEntity handlePaymentUpdate(OrderEntity order, BigDecimal difference, PaymentType paymentType, String idempotencyKey) {
        if (difference.compareTo(BigDecimal.ZERO) > 0) {
            // Cas 1 : Le nouveau montant est plus élevé -> Paiement du complément
            order.setStatus(OrderStatus.AWAITING_PAYMENT);
            orderRepository.save(order);
            return paymentService.processPayment(order, paymentType, Money.of(difference), idempotencyKey);
        } else {
            // Cas 2 : Le nouveau montant est moins élevé -> Remboursement de la différence
            return paymentService.refund(order, paymentType, Money.of(difference.abs()), idempotencyKey);
        }
    }

    private String generateIdempotencyKey(UUID orderUuid, String action) {
        return orderUuid + ":" + action + ":" + System.currentTimeMillis();
    }
}
