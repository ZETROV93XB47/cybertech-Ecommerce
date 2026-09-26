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
import com.novatech.cybertech.services.core.OrderCancellationTransactionalDelegate;
import com.novatech.cybertech.services.core.OrderManagementService;
import com.novatech.cybertech.services.core.OrderPriceCalculationService;
import com.novatech.cybertech.services.core.PaymentService;
import com.novatech.cybertech.services.core.StockService;
import com.novatech.cybertech.utils.ControllerSecurityUtils;
import com.novatech.cybertech.validator.core.OrderValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
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

    /**
     * Idempotency-key context token marking a payment retry. Combined with a monotonic
     * attempt counter (and a capture timestamp) so each retry of the same order produces
     * a DISTINCT key — see {@link #retryPayment(UUID, Jwt)}.
     */
    private static final String RETRY_PAYMENT_ACTION = "retry";

    private final OrderMapper orderMapper;

    private final StockService stockService;
    private final PaymentService paymentService;

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    private final OrderValidator orderValidatorChain;
    private final ApplicationEventPublisher eventPublisher;
    private final IdempotencyKeyServiceGenerator idempotencyKeyService;
    private final OrderPriceCalculationService orderPriceCalculationService;
    private final OrderCancellationTransactionalDelegate orderCancellationTransactionalDelegate;


    //TODO: refactor this method to make it callable only by an admin or separate this crud method in another service, a crud service for instance
    @Transactional(readOnly = true)
    public Collection<OrderResponseDto> getAll() {
        final List<OrderEntity> orders = orderRepository.findAll();
        hydrateOrderItemsAndProducts(orders);
        return orders.stream().map(orderMapper::mapFromEntityToResponseDto).toList();
    }

    /**
     * No-identity-arg overload kept for the {@link CrudBaseService}-style surface. It carries no IDOR
     * risk because it resolves the caller from the {@link org.springframework.security.core.context.SecurityContextHolder}
     * (via {@link ControllerSecurityUtils#currentCallerName()}) and delegates to the ownership-checked
     * {@link #getByUUID(UUID, String)} — so a regular USER only ever reads their own order, while a
     * {@code ROLE_ADMIN} caller bypasses the check. Not exposed on the {@link OrderManagementService}
     * interface; this guard is defence-in-depth should a future caller wire it up.
     */
    @Transactional(readOnly = true)
    public OrderResponseDto getByUUID(final UUID uuid) {
        return getByUUID(uuid, ControllerSecurityUtils.currentCallerName());
    }

    /**
     * Ownership-checked variant of {@link #getByUUID(UUID)}.
     *
     * <p>Resolves the order and verifies its initiator's {@code keycloakId} matches the
     * caller's JWT subject. Mirrors the CartServiceImp / {@link #getStatusByUUID(UUID, String)}
     * pattern: differing identities throw {@link OrderDoesntBelongsToUserException} (mapped
     * to HTTP 403 by {@code ErrorManagementController}). Used by the user-facing
     * {@code GET /order/get/{uuid}} endpoint to prevent IDOR.
     *
     * <p>Callers carrying {@code ROLE_ADMIN} bypass the ownership
     * check (resolved via {@link ControllerSecurityUtils#isCurrentCallerAdmin()}). USERs
     * still get the IDOR protection.</p>
     *
     * @param uuid       order UUID to fetch.
     * @param keycloakId caller's Keycloak subject; must equal the order's owner (USER role only).
     * @return the order DTO when ownership matches (USER) or unconditionally (ADMIN).
     * @throws OrderNotFoundException             when no order matches {@code uuid}.
     * @throws OrderDoesntBelongsToUserException  when a non-admin caller is not the order's initiator.
     */
    @Override
    @Transactional(readOnly = true)
    public OrderResponseDto getByUUID(final UUID uuid, final String keycloakId) {
        final OrderEntity order = orderRepository.findByUuid(uuid)
                .orElseThrow(() -> new OrderNotFoundException("No product with the UUID : " + uuid + " found"));

        assertCallerOwnsOrIsAdmin(order, keycloakId, uuid);

        return orderMapper.mapFromEntityToResponseDto(order);
    }

    /**
     * IDOR guard shared by the single-read, bulk-read and status-read paths: a non-admin caller must own
     * {@code order} (its initiator's {@code keycloakId} equals {@code keycloakId}); a {@code ROLE_ADMIN}
     * caller bypasses the check entirely. Centralised here so the ownership predicate lives in exactly one
     * place (was previously copy-pasted across {@link #getByUUID(UUID, String)} and
     * {@link #getStatusByUUID(UUID, String)}).
     *
     * @throws OrderDoesntBelongsToUserException when a non-admin caller is not the order's initiator.
     */
    private void assertCallerOwnsOrIsAdmin(final OrderEntity order, final String keycloakId, final UUID uuid) {
        if (!ControllerSecurityUtils.isCurrentCallerAdmin()
                && (order.getUserEntity() == null
                    || order.getUserEntity().getKeycloakId() == null
                    || !order.getUserEntity().getKeycloakId().equals(keycloakId))) {
            throw new OrderDoesntBelongsToUserException("Order " + uuid + " does not belong to the current user");
        }
    }

    /**
     * Lightweight status read with ownership check — used by the frontend's order-confirmation
     * polling loop after a Stripe payment so we don't refetch the full {@code OrderResponseDto}.
     *
     * <p>Callers carrying {@code ROLE_ADMIN} bypass the ownership check — mirrors the
     * admin escape-hatch already implemented in {@link #getByUUID(UUID, String)}. Without this,
     * admin support tooling would receive HTTP 403 when polling status on a customer's order.</p>
     */
    @Override
    @Transactional(readOnly = true)
    public OrderStatusDto getStatusByUUID(final UUID orderUuid, final String keycloakId) {
        final OrderEntity order = orderRepository.findByUuid(orderUuid)
                .orElseThrow(() -> new OrderNotFoundException("No order with the UUID : " + orderUuid + " found"));

        assertCallerOwnsOrIsAdmin(order, keycloakId, orderUuid);

        return OrderStatusDto.builder()
                .uuid(order.getUuid())
                .status(order.getStatus())
                .build();
    }

    /**
     * Bulk counterpart of {@link #getByUUID(UUID)} with the same defence-in-depth IDOR guard: a regular
     * USER may only read orders they own — if <em>any</em> requested UUID resolves to another user's order
     * the whole batch is rejected with {@link OrderDoesntBelongsToUserException} (fail-closed, consistent
     * with the single-read contract). A {@code ROLE_ADMIN} caller bypasses the check. Not exposed on the
     * {@link OrderManagementService} interface.
     */
    @Transactional(readOnly = true)
    public Collection<OrderResponseDto> getByUUIDs(final Collection<UUID> uuids) {
        final String keycloakId = ControllerSecurityUtils.currentCallerName();
        final List<OrderEntity> orders = orderRepository.findAllByUuidIn(uuids);
        orders.forEach(order -> assertCallerOwnsOrIsAdmin(order, keycloakId, order.getUuid()));
        return orders.stream().map(orderMapper::mapFromEntityToResponseDto).toList();
    }

    /**
     * Hard-delete an order after verifying ownership and state eligibility.
     *
     * <p>Resolves the caller via {@link #resolveKeycloakIdFromJwt(Jwt)}, surfacing a
     * {@link UserNotFoundException} instead of an NPE when the JWT {@code sub} claim is missing.
     * Stock is released before the row is deleted — no refund is issued here;
     * use {@link #cancelOrder(UUID, Jwt)} for that.
     *
     * @param uuid order UUID to delete.
     * @param jwt  caller identity; {@code sub} claim must be non-null.
     * @throws OrderNotFoundException              when no order matches {@code uuid}.
     * @throws OrderDoesntBelongsToUserException   when the caller is not the order's initiator.
     * @throws CannotCancelOrderException          when the order is not in a deletable state.
     * @throws UserNotFoundException               when the JWT subject is missing.
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
     * <p>Callers with a missing JWT {@code sub} now get a clean
     * {@link UserNotFoundException} rather than an NPE on {@code keycloakId.equals(null)}.
     *
     * @param dto update payload (new items, new shipping, new total).
     * @param jwt caller identity; {@code sub} claim must be non-null.
     * @throws OrderNotFoundException             when no order matches {@code dto.uuid}.
     * @throws OrderDoesntBelongsToUserException  when the caller is not the order's initiator.
     * @throws OrderAlreadyShippedException       when the order has already shipped.
     * @throws UserNotFoundException              when the JWT subject is missing.
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
        // products.isEmpty() used to default the total to ZERO and fall through to the
        // "no price difference" branch below, which flips the order to PAID — an all-nonexistent
        // product UUID list (findAllByUuidIn silently drops unmatched UUIDs, see
        // getAllProductsFromRequest) let a caller mark their own order PAID for €0 with no
        // payment. Reject instead of silently pricing at zero.
        if (products.isEmpty()) {
            throw new ProductNotFoundException("None of the requested products were found");
        }
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
                .shippingProvider(dto.getShippingProvider())
                .shippingType(dto.getShippingType())
                .build();

        final BigDecimal amount = orderPriceCalculationService.calculate(priceReq).getFinalAmount();
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

        // 7) Paiement attempt (idempotent) — quantities are part of the context (see placeOrder).
        // Only used for Cas 1 (new payment): Cas 2 (refund) needs the ORIGINAL payment's own
        // stored key instead, resolved inside handlePaymentUpdate — see its javadoc.
        final List<String> updatedProductUuidsWithQuantities = dto.getItemUpdateRequestDtoList().stream()
                .map(i -> i.getProductUuid() + ":" + i.getQuantity())
                .toList();
        final String updateIdempotencyKey = idempotencyKeyService.generateKey(order.getUuid().toString(), updatedProductUuidsWithQuantities);

        if (difference.compareTo(BigDecimal.ZERO) == 0) {
            // Cas 3 : Pas de différence de prix
            // On valide juste le stock et on s'assure que le statut est PAID
            if (paidAmount.compareTo(BigDecimal.ZERO) == 0) {
                throw new OrderNotFundedException(
                        "Cannot mark order " + order.getUuid() + " as PAID without any prior successful payment");
            }
            stockService.commitStock(order.getUuid());
            if (order.getStatus() != OrderStatus.PAID) {
                order.setStatus(OrderStatus.PAID);
            }
            final OrderEntity saved = orderRepository.save(order);
            return orderMapper.mapFromEntityToResponseDto(saved);
        }


        final PaymentEntity attempt = handlePaymentUpdate(order, difference, dto.getPaymentType(), updateIdempotencyKey);

        // Explicit save instead of relying on JPA dirty-check
        orderRepository.save(order);

        sendOrderUpdatedEvent(order, order.getUserEntity(), total.getAmount(), attempt.getStatus());

        return orderMapper.mapFromEntityToResponseDto(order);
    }

    /**
     * Retry a failed (or still-pending) payment for an existing order.
     *
     * <p><b>No double-discount on retry:</b> {@code order.getTotalAmount()} is the
     * post-discount final amount set once at {@link #placeOrder} time (placeOrder sums
     * {@code unitPrice * quantity} for every cart item — the unit price already reflects any
     * promotional discount applied upstream in the cart service). Retry forwards that stored total
     * verbatim to {@link PaymentService#processPayment}; NO discount strategy is re-applied here.
     * Consequently a customer who retries after a transient payment failure pays exactly the same
     * amount as the original attempt — never 2× the discount.
     *
     * <p>{@link #resolveKeycloakIdFromJwt(Jwt)} surfaces a
     * {@link UserNotFoundException} when the JWT {@code sub} claim is missing, instead of an NPE.
     *
     * <p>Side effects, in order:
     * <ol>
     *   <li>Re-reserves stock (the prior reservation was released on failure);
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
     * @throws UserNotFoundException               when the JWT subject is missing.
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

        // Guard against double-debit: Stripe confirms payment SYNCHRONOUSLY (setConfirm(true)) but
        // order.status only advances to PAID/PAYMENT_FAILED ASYNCHRONOUSLY via the Stripe webhook.
        // During that window order.status can still read AWAITING_PAYMENT even though a payment
        // already succeeded (or is currently in flight) — retrying here would fire a second real
        // Stripe PaymentIntent.
        final boolean paymentAlreadySettled = order.getPaymentAttempts().stream()
                .anyMatch(p -> p.getTransactionType() == TransactionType.PAYMENT
                        && (p.getStatus() == PaymentAttemptStatus.SUCCESS
                            || p.getStatus() == PaymentAttemptStatus.PROCESSING));
        if (paymentAlreadySettled) {
            throw new PaymentAlreadyCompletedForThisOrderException(
                    "A payment already succeeded or is in flight for order " + orderUuid);
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

        // 3. Tenter le paiement.
        // H-3 fix: the idempotency key MUST be unique per retry attempt. The old constant
        // key (orderUuid + "retry") made Stripe keep returning the cached FAILED result of
        // the first retry forever, and collided with the DB unique constraint on the second.
        // We now mix in the monotonic attempt counter (guarantees uniqueness) plus a capture
        // timestamp (human-readable trace of WHEN the retry fired). Both are evaluated once
        // here, so a transient @Retry of this very call still dedupes correctly at Stripe.
        final int retryAttemptNumber = order.getPaymentAttempts().size() + 1;
        final String retryTimestamp = LocalDateTime.now().toString();
        final String idempotencyKey = idempotencyKeyService.generateKey(order.getUuid().toString(), List.of(RETRY_PAYMENT_ACTION, String.valueOf(retryAttemptNumber), retryTimestamp));
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
     * <p>{@link #resolveKeycloakIdFromJwt(Jwt)} rejects a null JWT subject with
     * {@link UserNotFoundException} — no more NPE when the token is malformed.
     *
     * <p>The {@code totalAmount} persisted here is the FINAL,
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

        // 4) Total — delegated to OrderPriceCalculationService for proper discount support.
        // Price is read live from the product here — the cart itself carries no frozen price
        // (see CartItemEntity), so this is the moment the price actually gets locked in for the
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

        // 5) Items commande
        final List<OrderItemEntity> orderItems = cartItems.stream()
                .map(item -> OrderItemEntity.builder()
                        .unitPrice(item.getProductEntity().getPrice())
                        .quantity(item.getQuantity())
                        .subtotal(item.getProductEntity().getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
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

        // 8) Paiement attempt (idempotent) — quantities are part of the context so two orders
        // for the same products in different quantities never collide on the same key.
        final List<String> productUuidsWithQuantities = cartItems.stream()
                .map(i -> i.getProductEntity().getUuid() + ":" + i.getQuantity())
                .toList();
        final String idempotencyKey = idempotencyKeyService.generateKey(orderUuid.toString(), productUuidsWithQuantities);
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
     * <p>The JWT subject is resolved via {@link #resolveKeycloakIdFromJwt(Jwt)}
     * — a missing {@code sub} claim now throws {@link UserNotFoundException}.
     *
     * <p><b>Race-fix (full-suite IT regression):</b> the async {@code PaymentSucceededEvent}
     * listener ({@link com.novatech.cybertech.listener.OrderPaymentConfirmationEventListener#handlePaymentSuccess})
     * runs on a separate thread after the placeOrder TX commits, flipping the order to
     * {@link OrderStatus#PAID} and bumping the JPA {@code @Version}. If a user fires
     * {@code POST /cancel} fast enough, the cancel TX can collide with the listener's update and
     * the optimistic-lock failure would surface as HTTP 500. {@code @Retryable} bounds the work
     * to 3 attempts on {@link OptimisticLockingFailureException}; each attempt re-invokes
     * {@link OrderCancellationTransactionalDelegate#cancelWithinTransaction}, a SEPARATE bean —
     * crossing that bean boundary is what makes both the {@code @Transactional} and
     * {@code @Retryable} proxies fire on every attempt, so a retry re-fetches the order and sees
     * the listener's write instead of replaying a stale in-memory entity. The delegate's own
     * idempotency short-circuit (already-{@code CANCELED} → return as-is) covers the case where
     * the listener's write and the cancel actually land in the opposite order.
     *
     * @param orderUUID order to cancel
     * @param jwt       caller identity (subject must match the order's owner)
     * @return the cancelled order DTO
     */
    @Override
    @Retryable(retryFor = OptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 50))
    public OrderResponseDto cancelOrder(final UUID orderUUID, final Jwt jwt) {
        log.info("Order UUD : {}", orderUUID);
        return orderCancellationTransactionalDelegate.cancelWithinTransaction(orderUUID, jwt);
    }


    /**
     * Frontend-gap #1 — paginated read of the authenticated user's orders.
     *
     * <p>Resolves through the JPQL paginated finder
     * {@link OrderRepository#findByUserKeycloakIdAndOptionalStatuses}; an empty / null
     * status set is normalised to {@code null} so the WHERE clause's
     * {@code :statuses IS NULL OR ...} short-circuit returns every order. Read-only TX
     * mirrors the rest of the read-path methods in this service.</p>
     */
    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponseDto> findMyOrders(final String keycloakId, final Pageable pageable, final Set<OrderStatus> statuses) {
        final Set<OrderStatus> effectiveStatuses = (statuses == null || statuses.isEmpty()) ? null : statuses;
        final Page<OrderEntity> page = orderRepository.findByUserKeycloakIdAndOptionalStatuses(keycloakId, effectiveStatuses, pageable);
        hydrateOrderItemsAndProducts(page.getContent());
        return page.map(orderMapper::mapFromEntityToResponseDto);
    }

    /**
     * Frontend-gap #2 — admin-side paginated read with optional status / user filters.
     *
     * <p>Both filters short-circuit when null: passing {@code null} for both returns every
     * order in the system (paginated). Role enforcement is the controller's job; this
     * method assumes a trusted caller.</p>
     */
    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponseDto> findAllPaged(final Set<OrderStatus> statuses, final String userKeycloakId, final Pageable pageable) {
        final Set<OrderStatus> effectiveStatuses = (statuses == null || statuses.isEmpty()) ? null : statuses;
        final Page<OrderEntity> page = orderRepository.findAllByOptionalStatusesAndUserKeycloakId(effectiveStatuses, userKeycloakId, pageable);
        hydrateOrderItemsAndProducts(page.getContent());
        return page.map(orderMapper::mapFromEntityToResponseDto);
    }

    /**
     * Batch-loads {@code orderItemEntities} + {@code productEntity} for the given orders in one
     * extra query (see {@link OrderRepository#hydrateItemsAndProductsByIdIn}), instead of letting
     * {@link OrderMapper} trigger a lazy select per order and per item while mapping. A no-op for
     * an empty page/list.
     */
    private void hydrateOrderItemsAndProducts(final List<OrderEntity> orders) {
        if (orders.isEmpty()) {
            return;
        }
        orderRepository.hydrateItemsAndProductsByIdIn(orders.stream().map(OrderEntity::getId).toList());
    }

    private void validateUserBeforeProcessingPayment(final UserEntity userEntity) {
        final OrderValidationDto orderValidationDto = OrderValidationDto.builder()
                .isUserActive(userEntity.getIsActive())
                .userDefaultBankCard(Optional.ofNullable(userEntity.getBankCardEntity()).orElseThrow(() -> new BankCardNotFoundException("No bank card set, please, add a bank card and retry ...")))
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

    /**
     * {@code true} when the order's status is at or beyond {@link OrderStatus#SHIPPED}.
     * Used by {@link #updateOrder(OrderUpdateRequestDto, Jwt)} to short-circuit edits to a
     * physically dispatched order — the {@link OrderAlreadyShippedException} message it raises
     * ("already shipped") is only accurate from {@code SHIPPED} onwards, so this gate
     * intentionally still allows updates while the order sits in {@link OrderStatus#AWAITING_SHIPPING}.
     */
    private static boolean isOrderAlreadyShipped(OrderEntity orderEntity) {
        return orderEntity.getStatus().getCode() >= SHIPPED.getCode();
    }

    /**
     * Package-private (not {@code private}): also called by
     * {@link OrderCancellationTransactionalDelegateImp}, the separate bean
     * {@link #cancelOrder(UUID, Jwt)} delegates to.
     */
    static boolean isCurrentUserOrderInitiator(OrderEntity orderEntity, String keycloakId) {
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
     * Defensive null-guard around {@link Jwt#getSubject()}.
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
    static String resolveKeycloakIdFromJwt(final Jwt jwt) {
        return Optional.ofNullable(jwt)
                .map(Jwt::getSubject)
                .orElseThrow(() -> new UserNotFoundException("JWT subject missing — cannot resolve user"));
    }

    /**
     * @param idempotencyKey freshly-computed key for THIS update's item/quantity composition —
     *                       only meaningful for Cas 1 (a new charge for the updated composition).
     *                       Cas 2 ignores it: {@link PaymentService#refund} needs the ORIGINAL
     *                       payment's own stored key to look up which Stripe PaymentIntent to
     *                       refund against, not a key recomputed from the post-update item list
     *                       (which would almost never match any stored {@link PaymentEntity} row —
     *                       see progress.md for the bug this replaced).
     */
    private PaymentEntity handlePaymentUpdate(OrderEntity order, BigDecimal difference, PaymentType paymentType, String idempotencyKey) {
        if (difference.compareTo(BigDecimal.ZERO) > 0) {
            // Cas 1 : Le nouveau montant est plus élevé -> Paiement du complément
            order.setStatus(OrderStatus.AWAITING_PAYMENT);
            orderRepository.save(order);
            return paymentService.processPayment(order, paymentType, Money.of(difference), idempotencyKey);
        } else {
            // Cas 2 : Le nouveau montant est moins élevé -> Remboursement de la différence.
            // Refund against the most recent successful PAYMENT attempt — mirrors
            // OrderCancellationTransactionalDelegateImp's own lookup pattern.
            final String originalPaymentIdempotencyKey = order.getPaymentAttempts().stream()
                    .filter(p -> p.getTransactionType() == TransactionType.PAYMENT && p.getStatus() == PaymentAttemptStatus.SUCCESS)
                    .max(Comparator.comparing(BaseEntity::getCreatedAt))
                    .map(PaymentEntity::getIdempotencyKey)
                    .orElseThrow(() -> new NoPreviousPaymentAttemptException("No successful payment attempt found to refund for order " + order.getUuid()));
            return paymentService.refund(order, paymentType, Money.of(difference.abs()), originalPaymentIdempotencyKey);
        }
    }

}
