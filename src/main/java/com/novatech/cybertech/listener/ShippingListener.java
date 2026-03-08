package com.novatech.cybertech.listener;

import com.novatech.cybertech.dispatcher.NotificationDispatcher;
import com.novatech.cybertech.dispatcher.ShippingDispatcher;
import com.novatech.cybertech.dto.data.NotificationContext;
import com.novatech.cybertech.dto.data.ShippingContext;
import com.novatech.cybertech.dto.data.UserContactDto;
import com.novatech.cybertech.entities.OrderEntity;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.entities.enums.NotificationType;
import com.novatech.cybertech.entities.enums.OrderStatus;
import com.novatech.cybertech.events.OrderPaidEvent;
import com.novatech.cybertech.exceptions.OrderNotFoundException;
import com.novatech.cybertech.repositories.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.annotation.Transactional;


@Slf4j
@Component
@RequiredArgsConstructor
public class ShippingListener {

    private final OrderRepository orderRepository;
    private final ShippingDispatcher shippingDispatcher;
    private final NotificationDispatcher notificationDispatcher;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(final OrderPaidEvent orderPaidEvent) {
        log.info("OrderPaidEvent received");

        OrderEntity order = orderRepository.findByUuid(orderPaidEvent.getOrderUUID()).orElseThrow(() -> new OrderNotFoundException("Order " + orderPaidEvent.getOrderUUID() + " not found"));

        if (order.getStatus() != OrderStatus.PAID) return;

        final UserEntity user = order.getUserEntity();

        final UserContactDto userContactDto = UserContactDto.builder()
                .email(user.getEmail())
                .name(user.getFirstName())
                .phoneNumber(user.getPhoneNumber())
                .defaultCommunicationChanel(user.getFavoriteCommunicationChanel())
                .build();

        ShippingContext shippingContext = ShippingContext.builder()
                .user(userContactDto)
                .packageId(order.getUuid().toString())
                .payload(order)
                .shippingType(order.getShippingType())
                .shippingProvider(order.getShippingProvider())
                .build();

        shippingDispatcher.dispatch(shippingContext);

        order.setStatus(OrderStatus.SHIPPED);
        
        log.info("Order shipped successfully");
        
        orderRepository.save(order);

        NotificationContext notificationContext = NotificationContext.builder()
                .user(userContactDto)
                .notificationType(NotificationType.SHIPPING_CONFIRMATION)
                .payload(order)
                .build();

        notificationDispatcher.dispatch(notificationContext);

        log.info("Shipping Notification sent successfully");
    }
}
