package com.novatech.cybertech.services.implementation;


import com.github.f4b6a3.uuid.UuidCreator;
import com.novatech.cybertech.dto.data.OrderEventDto;
import com.novatech.cybertech.dto.data.OrderValidationDto;
import com.novatech.cybertech.dto.data.UserContactDto;
import com.novatech.cybertech.dto.request.order.OrderItemPriceDto;
import com.novatech.cybertech.dto.request.order.OrderPlacingRequestDto;
import com.novatech.cybertech.dto.request.order.OrderUpdateRequestDto;
import com.novatech.cybertech.dto.request.order.PriceCalculationRequestDto;
import com.novatech.cybertech.dto.request.orderItem.OrderItemCreateRequestDto;
import com.novatech.cybertech.dto.response.order.OrderResponseDto;
import com.novatech.cybertech.dto.response.order.OrderStatusDto;
import com.novatech.cybertech.dto.response.order.PriceCalculationResultDto;
import com.novatech.cybertech.entities.*;
import com.novatech.cybertech.entities.enums.*;
import com.novatech.cybertech.entities.valueObjects.Address;
import com.novatech.cybertech.entities.valueObjects.CurrencyCode;
import com.novatech.cybertech.entities.valueObjects.Money;
import com.novatech.cybertech.events.OrderCreatedEvent;
import com.novatech.cybertech.events.OrderUpdatedEvent;
import com.novatech.cybertech.exceptions.*;
import com.novatech.cybertech.mappers.entity.OrderMapper;
import com.novatech.cybertech.repositories.OrderRepository;
import com.novatech.cybertech.repositories.ProductRepository;
import com.novatech.cybertech.repositories.UserRepository;
import com.novatech.cybertech.services.core.IdempotencyKeyServiceGenerator;
import com.novatech.cybertech.services.core.OrderManagementService;
import com.novatech.cybertech.services.core.OrderPriceCalculationService;
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
    private final IdempotencyKeyServiceGenerator idempotencyKeyService;
    private final OrderPriceCalculationService orderPriceCalculationService;


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

    /**
     * Lightweight status read with ownership check — used by the frontend's order-confirmation
     * polling loop after a Stripe payment so we don't refetch the full {@code OrderResponseDto}.
     */
    @Override
    @Transactional(readOnly = true)
    public OrderStatusDto getStatusByUUID(final UUID orderUuid, final String keycloakId) {
        final OrderEntity order = orderRepository.findByUuid(orderUuid)
                .orElseThrow(() -> new OrderNotFoundException("No order with the UUID : " + orderUuid + " found"));

        if (order.getUserEntity() == null
                || order.getUserEntity().getKeycloakId() == null
                || !order.getUserEntity().getKeycloakId().equals(keycloakId)) {
            throw new OrderDoesntBelongsToUserException("Order " + orderUuid + " does not belong to the current user");
        }

        return OrderStatusDto.builder()
                .uuid(order.getUuid())
                .status(order.getStatus())
                .build();
    }

    //TODO: refactor this method to make it callable only by an admin or separate this crud method in another service, a crud service for instance
    @Transactional(readOnly = true)
    public Collection<OrderResponseDto> getByUUIDs(Collection<UUID> uuids) {
        return orderRepository.findAllByUuidIn(uuids).stream().map(orderMapper::mapFromEntityToResponseDto).toList();
    }

    //TODO: refactor this method to make it callable only by an admin or separate this crud method in another service, a crud service for instance

    /**
     * Hard-delete an order after verifying ownership and state eligibility.
     *
     * <p>BUG-054 guard: resolves the caller via {@link #resolveKeycloakIdFromJwt(Jwt)}, surfacing a
     * {@link UserNotFoundException} instead of an NPE when the JWT {@code sub} claim is missing.
     * Stock is released before the row is deleted — no refund is issued here;
     * use {@link #cancelOrder(UUID, Jwt)} for that.
     *
     * @param uuid order UUID to delete.
     * @param jwt  caller identity; {@code sub} claim must be non-null.
     * @throws OrderNotFoundException              when no order matches {@code uuid}.
     * @throws OrderDoesntBelongsToUserException   when the caller is not the order's initiator.
     * @throws CannotCancelOrderException          when the order is not in a deletable state.
     * @throws UserNotFoundException               when the JWT subject is missing (BUG-054).
     */
    @Override
    @Transactional
    public void deleteByUUID(final UUID uuid, final Jwt jwt) {

        final String keycloakId = resolveKeycloakIdFromJwt(jwt);
        final OrderEntity orderEntity = orderRepository.findByUuid(uuid).orElseThrow(() -> new OrderNotFoundException("Order not found"));

        if (!isCurrentUserOrderInitiator(orderEntity, keycloakId)) {
            throw new OrderDoesntBelongsToUserException("Order not found for this user account");
        }

        if (!isInDeletableState(orderEntity)) {
            log.info("Order is not in Deletable state, order current state : {}", orderEntity.getStatus().name());
            throw new CannotCancelOrderException("Order is not in Deletable state, order current state : " + orderEntity.getStatus().name());
        }

        stockService.releaseStock(uuid);
        orderRepository.deleteByUuid(uuid);
    }

    /**
     * Overwrite an existing order's items + shipping fields, then reconcile payment: charge the
     * positive delta, refund the negative delta, or commit the existing reservation when the
     * total is unchanged.
     *
     * <p>BUG-054 guard: callers with a missing JWT {@code sub} now get a clean
     * {@link UserNotFoundException} rather than an NPE on {@code keycloakId.equals(null)}.
     *
     * @param dto update payload (new items, new shipping, new total).
     * @param jwt caller identity; {@code sub} claim must be non-null.
     * @throws OrderNotFoundException             when no order matches {@code dto.uuid}.
     * @throws OrderDoesntBelongsToUserException  when the caller is not the order's initiator.
     * @throws OrderAlreadyShippedException       when the order has already shipped.
     * @throws UserNotFoundException              when the JWT subject is missing (BUG-054).
     */
    @Override
    @Transactional
    public OrderResponseDto updateOrder(final OrderUpdateRequestDto dto, final Jwt jwt) {

        log.info("in Update Order : {}", dto);

        final String keycloakId = resolveKeycloakIdFromJwt(jwt);

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

        // 3) Calculer le total — delegated to OrderPriceCalculationService (no re-discount on updates)
        final BigDecimal amount;
        if (products.isEmpty()) {
            // Guard: no matched products -> treat total as zero (preserves legacy behaviour for missing-product edge case)
            amount = BigDecimal.ZERO;
        } else {
            final List<OrderItemPriceDto> priceDtos = products.stream()
                    .map(p -> OrderItemPriceDto.builder()
                            .productUuid(p.getUuid())
                            .unitPrice(p.getPrice())
                            .quantity(quantities.get(p.getUuid()))
                            .build())
                    .toList();

            final PriceCalculationRequestDto priceReq = PriceCalculationRequestDto.builder()
                    .items(priceDtos)
                    .discountType(DiscountType.NO_DISCOUNT)
                    .currencyCode(CurrencyCode.fromCode("EUR"))
                    .build();

            amount = orderPriceCalculationService.calculate(priceReq).getFinalAmount();
        }
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
        final List<String> updatedProductUuids = dto.getItemUpdateRequestDtoList().stream()
                .map(i -> i.getProductUuid().toString())
                .toList();
        final String updateIdempotencyKey = idempotencyKeyService.generateKey(order.getUuid().toString(), updatedProductUuids);

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


        final PaymentEntity attempt = handlePaymentUpdate(order, difference, dto.getPaymentType(), updateIdempotencyKey);

        sendOrderUpdatedEvent(order, order.getUserEntity(), total.getAmount(), attempt.getStatus());

        return orderMapper.mapFromEntityToResponseDto(order);
    }

    /**
     * Retry a failed (or still-pending) payment for an existing order.
     *
     * <p><b>BUG-052 contract — no double-discount on retry:</b> {@code order.getTotalAmount()} is the
     * post-discount final amount set once at {@link #placeOrder} time (placeOrder sums
     * {@code unitPrice * quantity} for every cart item — the unit price already reflects any
     * promotional discount applied upstream in the cart service). Retry forwards that stored total
     * verbatim to {@link PaymentService#processPayment}; NO discount strategy is re-applied here.
     * Consequently a customer who retries after a transient payment failure pays exactly the same
     * amount as the original attempt — never 2× the discount.
     *
     * <p><b>BUG-054 guard:</b> {@link #resolveKeycloakIdFromJwt(Jwt)} surfaces a
     * {@link UserNotFoundException} when the JWT {@code sub} claim is missing, instead of an NPE.
     *
     * <p>Side effects, in order:
     * <ol>
     *   <li>Re-reserves stock (the prior reservation was released on failure — see BUG-050 fix);
     *       bubbles {@link com.novatech.cybertech.exceptions.NotEnoughStockException} if the stock
     *       is no longer available.</li>
     *   <li>Re-uses the {@link PaymentType} from the most recent attempt.</li>
     *   <li>Calls {@link PaymentService#processPayment} with the stored, already-discounted
     *       {@code order.getTotalAmount()}.</li>
     *   <li>Publishes an {@code OrderUpdatedEvent} with the final attempt status.</li>
     * </ol>
     *
     * @param orderUuid target order UUID.
     * @param jwt      caller identity; {@code sub} claim must be non-null.
     * @return the order mapped to {@link OrderResponseDto}.
     * @throws OrderNotFoundException              when no order matches {@code orderUuid}.
     * @throws OrderDoesntBelongsToUserException   when the caller is not the order's initiator.
     * @throws FailedRetryingPayment               when the order is not in a retryable state.
     * @throws NoPreviousPaymentAttemptException   when there is no prior attempt to copy the
     *                                             payment type from.
     * @throws UserNotFoundException               when the JWT subject is missing (BUG-054).
     */
    @Override
    @Transactional
    public OrderResponseDto retryPayment(final UUID orderUuid, final Jwt jwt) {
        final String keycloakId = resolveKeycloakIdFromJwt(jwt);
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
        final String idempotencyKey = idempotencyKeyService.generateKey(order.getUuid().toString(), "retry");
        final PaymentEntity attempt = paymentService.processPayment(
                order,
                paymentType,
                order.getTotalAmount(),
                idempotencyKey
        );

        log.info("Retry payment attempt :: {}", attempt);

        sendOrderUpdatedEvent(order, order.getUserEntity(), order.getTotalAmount().getAmount(), attempt.getStatus());

        return orderMapper.mapFromEntityToResponseDto(order);
    }


    /**
     * Convert the caller's cart into a persisted order, reserve stock, attempt payment, and
     * publish an {@code OrderCreatedEvent}.
     *
     * <p>BUG-054 guard: {@link #resolveKeycloakIdFromJwt(Jwt)} rejects a null JWT subject with
     * {@link UserNotFoundException} — no more NPE when the token is malformed.
     *
     * <p>Pinning for BUG-052: the {@code totalAmount} persisted here is the FINAL,
     * discount-adjusted amount (sum of cart-item unit prices × quantities — the cart service is
     * responsible for applying any promotional discount into the unit price before this call).
     * Any subsequent retry via {@link #retryPayment(UUID, Jwt)} forwards this amount verbatim.
     */
    @Override
    @Transactional
    public OrderResponseDto placeOrder(final OrderPlacingRequestDto req, final Jwt jwt) {

        final String keycloakId = resolveKeycloakIdFromJwt(jwt);
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

        // 4) Total — delegated to OrderPriceCalculationService for proper discount support
        final List<OrderItemPriceDto> priceDtos = cartItems.stream()
                .map(item -> OrderItemPriceDto.builder()
                        .productUuid(item.getProductEntity().getUuid())
                        .unitPrice(item.getUnitPrice())
                        .quantity(item.getQuantity())
                        .build())
                .toList();

        final PriceCalculationRequestDto priceRequest = PriceCalculationRequestDto.builder()
                .items(priceDtos)
                .discountType(req.getDiscountType())
                .currencyCode(CurrencyCode.fromCode("EUR"))
                .build();

        final PriceCalculationResultDto priceResult = orderPriceCalculationService.calculate(priceRequest);
        final Money totalMoney = priceResult.asFinalMoney();
        final BigDecimal totalAmount = totalMoney.getAmount();

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
                req.getDiscountType(),
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
        final List<String> productUuids = cartItems.stream().map(i -> i.getProductEntity().getUuid().toString()).toList();
        final String idempotencyKey = idempotencyKeyService.generateKey(orderUuid.toString(), productUuids);
        final PaymentEntity attempt;

        attempt = paymentService.processPayment(
                savedOrder,
                req.getPaymentType(),
                totalMoney,
                idempotencyKey
        );

        log.info("payment :: {}", attempt);

        if (attempt.getStatus() == PaymentAttemptStatus.FAILED) {
            log.warn("Payment FAILED for order {} — releasing stock reservation.", orderUuid);
            stockService.releaseStock(orderUuid);
        }

        sendOrderCreationEvent(savedOrder, user, totalAmount, attempt.getStatus());

        return orderMapper.mapFromEntityToResponseDto(savedOrder);
    }


    /**
     * Soft-cancel an order: flip status to {@code CANCELED} and refund every prior successful
     * payment attempt (excluding refunds themselves). Differs from {@link #deleteByUUID}, which
     * hard-deletes the row and issues no refund.
     *
     * <p>BUG-054 guard: the JWT subject is resolved via {@link #resolveKeycloakIdFromJwt(Jwt)}
     * — a missing {@code sub} claim now throws {@link UserNotFoundException}.
     */
    @Override
    @Transactional
    public OrderResponseDto cancelOrder(final UUID orderUUID, final Jwt jwt) {

        log.info("Order UUD : {}", orderUUID);

        final OrderEntity orderEntity = orderRepository.findByUuid(orderUUID).orElseThrow(() -> new OrderNotFoundException("Order with UUID " + orderUUID + " not found"));

        if (!isOrderAlreadyShipped(orderEntity)) {//Il faudra un autre endpoint pour annuler la commande une fois renvoyée
            final String keycloakId = resolveKeycloakIdFromJwt(jwt);
            if (isCurrentUserOrderInitiator(orderEntity, keycloakId)) {
                orderEntity.setStatus(OrderStatus.CANCELED);


                orderEntity.getPaymentAttempts().stream()
                        .filter(p -> p.getStatus() == PaymentAttemptStatus.SUCCESS)
                        .filter(p -> p.getTransactionType() == TransactionType.PAYMENT)
                        .forEach(paymentAttemptEntity -> paymentService.refund(orderEntity, paymentAttemptEntity.getPaymentType(), paymentAttemptEntity.getAmount(), paymentAttemptEntity.getIdempotencyKey()));


                //paymentService.refund(orderEntity, orderEntity.getPaymentAttempts().getLast().getPaymentType(), orderEntity.getTotalAmount(), generateIdempotencyKey(orderUUID, "cancel"));
                //TODO: check if it's better to user the calculated amountToRefund or the order totalAmount

                // Release any reserved stock for the cancelled order — mirrors deleteByUUID().
                // Without this, reservations leaked until the Redis TTL fired.
                stockService.releaseStock(orderUUID);

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
                                               final DiscountType discountType,
                                               final List<OrderItemEntity> orderItemEntities,
                                               final UserEntity user) {
        return OrderEntity.builder()
                .uuid(orderUuid)
                .userEntity(user)
                .orderItemEntities(orderItemEntities)
                .totalAmount(Money.of(totalPrice))
                .discountType(discountType)
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
        eventPublisher.publishEvent(new OrderCreatedEvent(this, buildOrderEventDto(orderEntity, user, totalPrice, paymentAttemptStatus)));
    }

    private void sendOrderUpdatedEvent(final OrderEntity orderEntity, final UserEntity user, final BigDecimal totalPrice, final PaymentAttemptStatus paymentAttemptStatus) {
        eventPublisher.publishEvent(new OrderUpdatedEvent(this, buildOrderEventDto(orderEntity, user, totalPrice, paymentAttemptStatus)));
    }

    private static OrderEventDto buildOrderEventDto(
            final OrderEntity orderEntity,
            final UserEntity user,
            final BigDecimal totalPrice,
            final PaymentAttemptStatus paymentAttemptStatus
    ) {
        return OrderEventDto.builder()
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
        final Set<OrderStatus> deletableStates = Set.of(CREATED, AWAITING_PAYMENT, PAYMENT_FAILED, DELIVERED, RETURNED, CANCELED, REFUNDED);
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

    /**
     * BUG-054: defensive null-guard around {@link Jwt#getSubject()}.
     *
     * <p>Every user-facing method in this service identifies the caller by the JWT {@code sub}
     * claim. A malformed / stripped token (missing sub) used to fall through to
     * {@code userRepository.findByKeycloakId(null)} — an NPE waiting to happen, or worse a silent
     * query-by-null returning the first-inserted user on some JPA providers. This helper surfaces
     * the condition up-front as {@link UserNotFoundException} (HTTP 404 via the advice) instead.
     *
     * @param jwt caller token; may itself be null — both cases are treated as "no identity".
     * @return the non-null Keycloak subject.
     * @throws UserNotFoundException when the JWT is null or has a null {@code sub} claim.
     */
    private static String resolveKeycloakIdFromJwt(final Jwt jwt) {
        return Optional.ofNullable(jwt)
                .map(Jwt::getSubject)
                .orElseThrow(() -> new UserNotFoundException("JWT subject missing — cannot resolve user"));
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

}
