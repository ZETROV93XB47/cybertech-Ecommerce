package com.novatech.cybertech.batch.task;

import com.novatech.cybertech.batch.base.BaseTasklet;
import com.novatech.cybertech.dispatcher.NotificationDispatcher;
import com.novatech.cybertech.dispatcher.ShippingDispatcher;
import com.novatech.cybertech.dto.data.NotificationContext;
import com.novatech.cybertech.dto.data.ShippingContext;
import com.novatech.cybertech.dto.data.UserContactDto;
import com.novatech.cybertech.entities.OrderEntity;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.entities.enums.NotificationType;
import com.novatech.cybertech.entities.enums.OrderStatus;
import com.novatech.cybertech.repositories.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShipAllPaidOrdersTasklet extends BaseTasklet {

    private final OrderRepository orderRepository;
    private final ShippingDispatcher shippingDispatcher;
    private final NotificationDispatcher notificationDispatcher;

    @Override
    @Transactional
    public RepeatStatus execute(StepContribution stepContribution, StepArguments stepArguments) {
        log.info("Starting ShipAllAwaitingShippingOrdersTasklet");

        // Récupérer toutes les commandes en attente d'expédition
        List<OrderEntity> awaitingOrders = orderRepository.findByStatus(OrderStatus.PAID);

        if (awaitingOrders.isEmpty()) {
            log.info("No orders found in PAID status.");
            stepContribution.setExitStatus(ExitStatus.COMPLETED);
            return RepeatStatus.FINISHED;
        }

        log.info("Found {} orders to ship.", awaitingOrders.size());

        awaitingOrders.forEach(order -> {
            try {
                processShipping(order);
            } catch (Exception e) {
                log.error("Error processing shipping for order {}", order.getUuid(), e);
                // On continue pour les autres commandes même si une échoue
            }
        });

        stepContribution.setExitStatus(ExitStatus.COMPLETED);
        log.info("ShipAllAwaitingShippingOrdersTasklet finished");
        return RepeatStatus.FINISHED;
    }

    private void processShipping(OrderEntity order) {
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
        orderRepository.save(order);

        NotificationContext notificationContext = NotificationContext.builder()
                .user(userContactDto)
                .notificationType(NotificationType.SHIPPING_CONFIRMATION)
                .payload(order)
                .build();

        notificationDispatcher.dispatch(notificationContext);
        log.info("Order {} shipped via Batch.", order.getUuid());
    }
}