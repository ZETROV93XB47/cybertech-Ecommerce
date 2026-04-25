package com.novatech.cybertech.batch.task;

import com.novatech.cybertech.batch.base.BaseTasklet;
import com.novatech.cybertech.dispatcher.NotificationDispatcher;
import com.novatech.cybertech.dto.data.NotificationContext;
import com.novatech.cybertech.dto.data.ShippingContext;
import com.novatech.cybertech.dto.data.UserContactDto;
import com.novatech.cybertech.entities.OrderEntity;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.entities.enums.NotificationType;
import com.novatech.cybertech.entities.enums.OrderStatus;
import com.novatech.cybertech.repositories.OrderRepository;
import com.novatech.cybertech.services.implementation.ShippingConfirmationPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShipAllPaidOrdersTasklet extends BaseTasklet {

    private final OrderRepository orderRepository;
    private final NotificationDispatcher notificationDispatcher;
    private final ShipOrderTransactionalDelegate shipOrderDelegate;

    @Override
    public RepeatStatus execute(StepContribution stepContribution, StepArguments stepArguments) {
        log.info("Starting ShipAllAwaitingShippingOrdersTasklet");

        // Récupérer toutes les commandes en attente d'expédition.
        // findByStatus runs in its own short transaction (Spring Data default) — we no longer
        // wrap execute(...) in @Transactional because the per-order optimistic-lock claim must
        // commit at the delegate boundary so a race-loss can be caught here per-order.
        final List<OrderEntity> awaitingOrders = orderRepository.findByStatus(OrderStatus.PAID);

        if (awaitingOrders.isEmpty()) {
            log.info("No orders found in PAID status.");
            stepContribution.setExitStatus(ExitStatus.COMPLETED);
            return RepeatStatus.FINISHED;
        }

        log.info("Found {} orders to ship.", awaitingOrders.size());

        awaitingOrders.forEach(order -> {
            try {
                processShipping(order);
            }
            catch (OptimisticLockingFailureException e) {
                // Catches both OptimisticLockingFailureException and its subclass
                // ObjectOptimisticLockingFailureException raised by Hibernate/Spring ORM.
                // Lost the race against ShippingListener (which also ships PAID orders on
                // OrderPaidEvent). Optimistic locking via @Version on BaseEntity guarantees
                // exactly one of the two paths wins the claim — the loser silently skips.
                // Wave 3 fix: the claim+save now lives inside ShipOrderTransactionalDelegate
                // (REQUIRES_NEW), so the optimistic-lock flush happens at delegate-method exit
                // and this catch can actually observe the race-loss per order.
                log.debug("Skipping order {} — claimed concurrently by another path: {}", order.getUuid(), e.getMessage());
            }
            catch (Exception e) {
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
                .shippingType(order.getShippingType())
                .shippingProvider(order.getShippingProvider())
                .build();

        // Atomically claim the order before dispatching: flip PAID -> AWAITING_SHIPPING and save
        // FIRST so JPA's @Version optimistic locking can detect the race against ShippingListener
        // (which also ships PAID orders on OrderPaidEvent). The claim+dispatch run in their own
        // REQUIRES_NEW tx so the optimistic-lock flush commits inside this call and can be
        // observed by the per-order try/catch in execute(...).
        shipOrderDelegate.claimAndShip(order, shippingContext);

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

        notificationDispatcher.dispatch(notificationContext);
        log.info("Order {} shipped via Batch.", order.getUuid());
    }
}
