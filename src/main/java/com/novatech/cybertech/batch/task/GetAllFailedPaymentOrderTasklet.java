package com.novatech.cybertech.batch.task;

import com.novatech.cybertech.batch.base.BaseTasklet;
import com.novatech.cybertech.entities.OrderEntity;
import com.novatech.cybertech.entities.enums.OrderStatus;
import com.novatech.cybertech.repositories.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.novatech.cybertech.constants.CyberTechAppConstants.FAILED_PAYMENT_ORDERS_MAP_BY_USERS;
import static com.novatech.cybertech.constants.CyberTechAppConstants.NO_FAILED_PAYMENT_ORDER_FOUND;
import static org.springframework.batch.core.ExitStatus.COMPLETED;


@Slf4j
@Service
@RequiredArgsConstructor
public class GetAllFailedPaymentOrderTasklet extends BaseTasklet {

    private final OrderRepository orderRepository;


    /**
     * Queries orders that are CURRENTLY {@link OrderStatus#PAYMENT_FAILED} — not payment attempts
     * that were ever marked FAILED. A retried payment leaves the old {@code PaymentEntity} row at
     * FAILED forever (see {@code OrderPaymentConfirmationEventListener}) even once the order moves
     * on to PAID/SHIPPED/CANCELED/etc., so querying payment-attempt history used to keep resurfacing
     * long-completed orders in the "please retry your payment" reminder email.
     */
    @Override
    @Transactional
    public RepeatStatus execute(final StepContribution stepContribution, final StepArguments stepArguments) {

        log.info("Starting GetAllFailedPaymentOrderTasklet");

        final List<OrderEntity> ordersStillAwaitingPaymentRetry = orderRepository.findByStatus(OrderStatus.PAYMENT_FAILED);

        final Map<String, List<UUID>> failedPaymentsMapUserEmailByUserEmail = ordersStillAwaitingPaymentRetry.stream()
                .collect(Collectors.groupingBy(o -> o.getUserEntity().getEmail(), Collectors.mapping(OrderEntity::getUuid, Collectors.toList())));


        if (failedPaymentsMapUserEmailByUserEmail.isEmpty()) {
            stepContribution.setExitStatus(new ExitStatus(NO_FAILED_PAYMENT_ORDER_FOUND));
            log.info("No failed payment orders found");
        }

        else {
            stepContribution.getStepExecution().getJobExecution().getExecutionContext().put(FAILED_PAYMENT_ORDERS_MAP_BY_USERS, failedPaymentsMapUserEmailByUserEmail);
            stepContribution.setExitStatus(COMPLETED);
        }

        log.info("GetAllFailedPaymentOrderTasklet finished");

        return RepeatStatus.FINISHED;
    }
}
