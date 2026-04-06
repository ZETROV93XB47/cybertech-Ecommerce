package com.novatech.cybertech.listener;

import com.novatech.cybertech.dispatcher.ShippingDispatcher;
import com.novatech.cybertech.dto.data.NotificationContext;
import com.novatech.cybertech.dto.data.OrderEventDto;
import com.novatech.cybertech.dto.data.ShippingContext;
import com.novatech.cybertech.dto.data.UserContactDto;
import com.novatech.cybertech.entities.OrderEntity;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.entities.enums.NotificationType;
import com.novatech.cybertech.entities.enums.OrderStatus;
import com.novatech.cybertech.events.OrderPaidEvent;
import com.novatech.cybertech.events.OrderShippedEvent;
import com.novatech.cybertech.exceptions.OrderNotFoundException;
import com.novatech.cybertech.repositories.OrderRepository;
import com.novatech.cybertech.services.implementation.ShippingConfirmationPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import static com.novatech.cybertech.constants.CyberTechAppConstants.APPLICATION_ASYNC_TASK_EXECUTOR;


@Slf4j
@Component
@RequiredArgsConstructor
public class ShippingListener {

    private final OrderRepository orderRepository;
    private final ShippingDispatcher shippingDispatcher;
    private final ApplicationEventPublisher eventPublisher;


    @Async(APPLICATION_ASYNC_TASK_EXECUTOR)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(final OrderPaidEvent orderPaidEvent) {
        log.info("OrderPaidEvent received");

        final OrderEntity order = orderRepository.findByUuid(orderPaidEvent.getOrderUUID()).orElseThrow(() -> new OrderNotFoundException("Order " + orderPaidEvent.getOrderUUID() + " not found"));

        if (order.getStatus() != OrderStatus.PAID) return;

        final UserEntity user = order.getUserEntity();

        final UserContactDto userContactDto = UserContactDto.builder()
                .email(user.getEmail())
                .name(user.getFirstName())
                .phoneNumber(user.getPhoneNumber())
                .defaultCommunicationChanel(user.getFavoriteCommunicationChanel())
                .build();

        final ShippingContext shippingContext = ShippingContext.builder()
                .user(userContactDto)
                .packageId(order.getUuid().toString())
                .shippingType(order.getShippingType())
                .shippingProvider(order.getShippingProvider())
                .build();

        shippingDispatcher.dispatch(shippingContext);

        log.info("Order shipped successfully");

        order.setStatus(OrderStatus.SHIPPED);

        orderRepository.save(order);

        final ShippingConfirmationPayload payload = ShippingConfirmationPayload.builder()
                .orderUuid(order.getUuid())
                .shippingType(order.getShippingType())
                .shippingProvider(order.getShippingProvider())
                .userName(order.getUserEntity().getFirstName())
                .build();

        final NotificationContext notificationContext = NotificationContext.builder()
                .user(userContactDto)
                .notificationType(NotificationType.SHIPPING_CONFIRMATION)
                .payload(payload)
                .build();

        final OrderEventDto orderEventDto = OrderEventDto.builder()
                .orderUuid(order.getUuid())
                .totalAmount(order.getTotalAmount().getAmount())
                .orderStatus(order.getStatus())
                .userContactDto(userContactDto)
                .shippingType(order.getShippingType())
                .shippingProvider(order.getShippingProvider())
                .paymentAttemptStatus(order.getPaymentAttempts().getLast().getStatus())
                .build();


        eventPublisher.publishEvent(new OrderShippedEvent(orderEventDto));
        log.info("OrderShippedEvent published successfully");
    }
}
