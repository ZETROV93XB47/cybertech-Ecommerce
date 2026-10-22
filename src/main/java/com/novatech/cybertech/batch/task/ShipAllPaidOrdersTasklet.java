package com.novatech.cybertech.batch.task;

import com.novatech.cybertech.batch.base.BaseTasklet;
import com.novatech.cybertech.dto.data.NotificationContext;
import com.novatech.cybertech.dto.data.ShippingContext;
import com.novatech.cybertech.dto.data.UserContactDto;
import com.novatech.cybertech.entities.OrderEntity;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.entities.enums.NotificationType;
import com.novatech.cybertech.entities.enums.OrderStatus;
import com.novatech.cybertech.repositories.OrderRepository;
import com.novatech.cybertech.services.core.NotificationRetryableDelivery;
import com.novatech.cybertech.services.implementation.ShippingConfirmationPayload;
import com.novatech.cybertech.utils.UserContactUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShipAllPaidOrdersTasklet extends BaseTasklet {

    private final OrderRepository orderRepository;
    private final NotificationRetryableDelivery notificationRetryableDelivery;
    private final ShipOrderTransactionalDelegate shipOrderDelegate;

    /**
     * How long a PAID order stays modifiable before it is shipped. This tasklet is the only
     * shipping path: shipping on the payment event left no window to {@code updateOrder} at all.
     */
    @Value("${cybertech.shipping.grace-period:30m}")
    private Duration gracePeriod;

    @Override
    public RepeatStatus execute(StepContribution stepContribution, StepArguments stepArguments) {
        log.info("Starting ShipAllAwaitingShippingOrdersTasklet");

        // Récupérer toutes les commandes en attente d'expédition.
        // execute(...) still runs inside the step's chunk transaction (BatchConfig wires the
        // tasklet with the platform transaction manager), so these entities are managed by that
        // outer session. The per-order claim commits in the delegate's own REQUIRES_NEW
        // transaction on a re-loaded copy — these instances must stay unmodified, or the step's
        // commit would flush them at a stale @Version.
        final LocalDateTime paidBefore = LocalDateTime.now().minus(gracePeriod);
        final List<OrderEntity> awaitingOrders = orderRepository.findByStatusPaidBefore(OrderStatus.PAID, paidBefore);

        if (awaitingOrders.isEmpty()) {
            log.info("No PAID orders past their {} modification window.", gracePeriod);
            stepContribution.setExitStatus(ExitStatus.COMPLETED);
            return RepeatStatus.FINISHED;
        }

        log.info("Found {} orders to ship.", awaitingOrders.size());

        awaitingOrders.forEach(order -> {
            try {
                processShipping(order, paidBefore);
            }
            catch (OptimisticLockingFailureException e) {
                // Catches both OptimisticLockingFailureException and its subclass
                // ObjectOptimisticLockingFailureException raised by Hibernate/Spring ORM.
                // The order changed under us: a concurrent updateOrder (the customer edited it
                // right as its modification window closed) or an overlapping run of this step.
                // Optimistic locking via @Version on BaseEntity guarantees the claim never
                // overrides that change — the order is skipped and re-examined on the next run.
                // The claim+save now lives inside ShipOrderTransactionalDelegate
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

    private void processShipping(final OrderEntity order, final LocalDateTime paidBefore) {
        final UserEntity user = order.getUserEntity();
        if (user == null) {
            // Structurally possible — userEntity is the only nullable column on orderTable — but
            // never legitimately reachable (an order cannot be placed without a user). Thrown here
            // so it lands in the per-order catch in execute(...) with a clear, actionable message
            // instead of a bare NPE.
            throw new IllegalStateException("Order " + order.getUuid() + " has no associated user — cannot resolve shipping contact details.");
        }

        final UserContactDto userContactDto = UserContactUtils.toUserContact(user);

        ShippingContext shippingContext = ShippingContext.builder()
                .user(userContactDto)
                .packageId(order.getUuid().toString())
                .shippingType(order.getShippingType())
                .shippingProvider(order.getShippingProvider())
                .build();

        // Atomically claim the order, dispatch, and persist the terminal SHIPPED status — all in
        // one REQUIRES_NEW transaction inside the delegate. The PAID -> AWAITING_SHIPPING claim
        // flips first so JPA's @Version optimistic locking detects a concurrent change (updateOrder
        // or an overlapping run of this step); the loser throws OptimisticLockingFailureException,
        // caught per-order in execute(...). The SHIPPED transition
        // now lives inside the delegate too — doing it here on the detached `order` after the
        // delegate already bumped @Version failed the save with a stale-version optimistic lock.
        //
        // A false return means the order was already shipped once (shippedAt already set) and was
        // re-promoted to PAID by an updateOrder top-up in between — no dispatch happened, so no
        // shipping-confirmation notification must be sent either.
        if (!shipOrderDelegate.claimAndShip(order, shippingContext, paidBefore)) {
            return;
        }

        final ShippingConfirmationPayload payload = ShippingConfirmationPayload.builder()
                .orderUuid(order.getUuid())
                .shippingType(order.getShippingType())
                .shippingProvider(order.getShippingProvider())
                .userName(user.getFirstName())
                .build();

        final NotificationContext notificationContext = NotificationContext.builder()
                .user(userContactDto)
                .notificationType(NotificationType.SHIPPING_CONFIRMATION)
                .communicationChanel(userContactDto.getDefaultCommunicationChanel())
                .payload(payload)
                .build();

        // Used to call notificationDispatcher.dispatch(...) directly, which skips both
        // the Resilience4j retry policy and the NotificationOutcomeRecorder audit trail that
        // every other notification call site in the app goes through. A transient mail-server
        // blip here used to be silently dropped — no retry, no PENDING_RETRY row for the Phase 3
        // redrive tasklet to pick up, and no way to know the shipping confirmation was ever lost.
        notificationRetryableDelivery.deliver(notificationContext);
        log.info("Order {} shipped via Batch.", order.getUuid());
    }
}
