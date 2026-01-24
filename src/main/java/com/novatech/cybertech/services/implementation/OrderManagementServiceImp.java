package com.novatech.cybertech.services.implementation;


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
import com.novatech.cybertech.entities.enums.ShippingProvider;
import com.novatech.cybertech.entities.enums.ShippingType;
import com.novatech.cybertech.entities.valueObjects.Money;
import com.novatech.cybertech.events.OrderCreatedEvent;
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
import java.util.*;
import java.util.stream.Collectors;

import static com.novatech.cybertech.entities.enums.DiscountType.NO_DISCOUNT;
import static com.novatech.cybertech.entities.enums.OrderStatus.*;
import static com.novatech.cybertech.entities.enums.PaymentStatus.SUCCESS;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderManagementServiceImp implements OrderManagementService {

    private final OrderMapper orderMapper;

    private final StockService stockService;
    private final PaymentService paymentService;

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    private final OrderValidator orderValidatorChain;
    private final ShippingDispatcher shippingDispatcher;
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
    public OrderResponseDto updateOrder(final OrderUpdateRequestDto orderUpdateRequestDto, final Jwt jwt) {

        final String keycloakId = jwt.getSubject();

        final OrderEntity orderEntity = orderRepository.findByUuid(orderUpdateRequestDto.getOrderUuid()).orElseThrow(() -> new OrderNotFoundException("Order not found"));
        final UserEntity userEntity = orderEntity.getUserEntity();

        if (isCurrentUserOrderInitiator(orderEntity, keycloakId) && isOrderAlreadyShipped(orderEntity)) {

            // 2. Charger les produits
            List<ProductEntity> products = getAllProductsFromRequest(orderUpdateRequestDto.getItemUpdateRequestDtoList());
            Map<UUID, Integer> quantities = orderUpdateRequestDto.getItemUpdateRequestDtoList().stream().collect(Collectors.toMap(OrderItemCreateRequestDto::getProductUuid, OrderItemCreateRequestDto::getQuantity));

            // 4. Vérifier la commande
            validateOrderBeforeProcessingPayment(quantities, orderEntity.getUserEntity());

            // 3. Réserver le stock AVANT tout paiement
            stockService.reserveStock(orderUpdateRequestDto.getOrderUuid(), quantities);

            // 5. Calculer le prix total
            final BigDecimal amount = processOrderPaymentAmount(orderUpdateRequestDto.getItemUpdateRequestDtoList(), products);
            orderEntity.setTotalAmount(Money.of(amount));

            // 6. Paiement
            final PaymentEntity payment = paymentService.processPayment(orderUpdateRequestDto.getPaymentType(), amount);

            return switch (payment.getPaymentStatus()) {
                case SUCCESS -> onPaymentSuccess(orderUpdateRequestDto.getShippingType(), orderUpdateRequestDto.getShippingProvider(), orderEntity.getUuid(), orderEntity, userEntity, amount, quantities);
                case FAILED -> throw new FailedUpdatingOrder("Could not process payment");
            };
        }
        throw new OrderDoesntBelongsToUserException("Order not found for this user account");
    }


    @Override
    @Transactional
    public OrderResponseDto placeOrder(final OrderPlacingRequestDto req, final Jwt jwt) {

        // 1. Charger l'utilisateur
        String keycloakId = jwt.getSubject();
        final UserEntity userEntity = userRepository.findByKeycloakId(keycloakId).orElseThrow(() -> new UserNotFoundException("User not found"));

        // 2. Charger les produits
        List<ProductEntity> products = getAllProductsFromRequest(req.getOrderItems());
        Map<UUID, Integer> quantities = req.getOrderItems().stream().collect(Collectors.toMap(OrderItemCreateRequestDto::getProductUuid, OrderItemCreateRequestDto::getQuantity));
        final UUID orderUUID = UUID.randomUUID();

        // 3. Réserver le stock AVANT tout paiement
        stockService.reserveStock(orderUUID, quantities);

        // 4. Vérifier la commande
        validateOrderBeforeProcessingPayment(quantities, userEntity);

        // 5. Calculer le prix total
        final BigDecimal amount = processOrderPaymentAmount(req.getOrderItems(), products);

        // 6. Paiement
        final PaymentEntity payment = paymentService.processPayment(req.getPaymentType(), amount);

        // 7. Construire l’OrderEntity
        final OrderEntity order = orderMapper.mapFromOrderPlacingRequestDtoToOrderEntity(req);
        final List<OrderItemEntity> items = getOrderItemEntities(req, order, products);

        initOrderEntity(orderUUID, order, payment, amount, items, userEntity);

        return switch (payment.getPaymentStatus()) {
            case SUCCESS ->
                    onPaymentSuccess(req.getShippingType(), req.getShippingProvider(), orderUUID, order, userEntity, amount, quantities);
            case FAILED -> onPaymentFailure(orderUUID, order);
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


    private OrderResponseDto onPaymentFailure(final UUID orderUUID, final OrderEntity order) {
        // Paiement échec → libérer le stock
        stockService.releaseStock(orderUUID);
        order.setStatus(PENDING_PAYMENT);
        OrderEntity saved = orderRepository.save(order);

        return orderMapper.mapFromEntityToResponseDto(saved);
        //throw new PaymentFailedException("Could not process payment");
    }


    private OrderResponseDto onPaymentSuccess(final ShippingType shippingType, final ShippingProvider shippingProvider, final UUID orderUUID, final OrderEntity order, final UserEntity userEntity, final BigDecimal amount, final Map<UUID, Integer> quantities) {
        // 8. Commit du stock
        stockService.commitStock(orderUUID);

        OrderEntity saved = orderRepository.save(order);

        // 9. Événements
        sendOrderCreationEvent(order, userEntity, amount, quantities);
        sendOrderShippingEvent(shippingType, shippingProvider, userEntity, saved);

        return orderMapper.mapFromEntityToResponseDto(saved);
    }


    private void validateOrderBeforeProcessingPayment(final Map<UUID, Integer> productsByQuantityMap, final UserEntity userEntity) {
        final OrderValidationDto orderValidationDto = OrderValidationDto.builder()
                .isUserActive(userEntity.getIsActive())
                .userDefaultBankCard(userEntity.getBankCardEntities().stream().filter(BankCardEntity::getIsDefault).findFirst().orElseThrow(() -> new NoDefaultBankCartSetException("No default bank card set, please, set a default bank card and retry ...")))
                .build();

        orderValidatorChain.validate(orderValidationDto);
    }


    private static void initOrderEntity(final UUID orderUUID, final OrderEntity orderEntity, final PaymentEntity paymentEntity, final BigDecimal totalPrice, final List<OrderItemEntity> orderItemEntities, final UserEntity user) {
        orderEntity.setUuid(orderUUID);
        orderEntity.setPaymentEntity(paymentEntity);
        orderEntity.setTotalAmount(Money.of(totalPrice));
        orderEntity.setStatus(PROCESSING);
        orderEntity.setOrderItemEntities(orderItemEntities);
        orderEntity.setUserEntity(user);
        orderEntity.setDiscountType(NO_DISCOUNT);
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


    private void sendOrderCreationEvent(final OrderEntity orderEntity, final UserEntity user, final BigDecimal totalPrice, final Map<UUID, Integer> productsByQuantityMap) {

        OrderEventDto orderEventDto = OrderEventDto.builder()
                .orderUuid(orderEntity.getUuid())
                .orderStatus(orderEntity.getStatus())
                .paymentStatus(SUCCESS)
                .totalAmount(totalPrice)
                .productsByQuantityMap(productsByQuantityMap)
                .userContactDto(UserContactDto.builder()
                        .defaultCommunicationChanel(user.getFavoriteCommunicationChanel())
                        .email(user.getEmail())
                        .name(user.getFirstName())
                        .phoneNumber(user.getPhoneNumber())
                        .build())
                .build();

        eventPublisher.publishEvent(new OrderCreatedEvent(this, orderEventDto));
    }


    private static List<OrderItemEntity> getOrderItemEntities(final OrderPlacingRequestDto orderPlacingRequestDto, final OrderEntity orderEntity, final List<ProductEntity> productEntities) {
        final Map<UUID, Integer> productsByQuantityMap = orderPlacingRequestDto.getOrderItems().stream().collect(Collectors.toMap(OrderItemCreateRequestDto::getProductUuid, OrderItemCreateRequestDto::getQuantity));

        return productEntities.stream().map(productEntity -> OrderItemEntity.builder()
                        .uuid(UUID.randomUUID())
                        .unitPrice(productEntity.getPrice())
                        .quantity(productsByQuantityMap.get(productEntity.getUuid()))
                        .subtotal(productEntity.getPrice().multiply(BigDecimal.valueOf(productsByQuantityMap.get(productEntity.getUuid()))))
                        .orderEntity(orderEntity)//!Warning: Ici, l'objet orderEntity n'est pas encore complet
                        .productEntity(productEntity)
                        .build())
                .collect(Collectors.toList());
    }


    private BigDecimal processOrderPaymentAmount(final List<OrderItemCreateRequestDto> orderItemCreateRequestDto, final List<ProductEntity> productEntities) {

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
        final Set<OrderStatus> deletableStates = Set.of(PENDING_PAYMENT, DELIVERED, RETURNED, CANCELED, REFUNDED);
        return deletableStates.contains(order.getStatus());
    }
}
