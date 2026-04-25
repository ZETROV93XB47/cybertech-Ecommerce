package com.novatech.cybertech.batch.task;

import com.novatech.cybertech.batch.base.BaseTasklet;
import com.novatech.cybertech.entities.BaseEntity;
import com.novatech.cybertech.entities.OrderEntity;
import com.novatech.cybertech.entities.enums.OrderStatus;
import com.novatech.cybertech.repositories.OrderRepository;
import com.novatech.cybertech.services.core.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.novatech.cybertech.constants.CyberTechAppConstants.NO_ORDERS_TO_CANCEL;
import static com.novatech.cybertech.constants.CyberTechAppConstants.PENDING_ORDERS_MAP_BY_USER_EMAIL;
import static org.springframework.batch.core.ExitStatus.COMPLETED;


@Slf4j
@Service
@RequiredArgsConstructor
public class CancelAllPendingOrdersByTimeTasklet extends BaseTasklet {

    @Value("${time-before-deleting-order}")
    private int timeBeforeDeletingOrder;

    private final OrderRepository orderRepository;
    private final StockService stockService;


    @Override
    @Transactional
    public RepeatStatus execute(final StepContribution stepContribution, final StepArguments stepArguments) {

        log.info("Starting CancelAllPendingOrdersByTimeTasklet");

        final List<OrderEntity> ordersToCancel = orderRepository.findByStatusAndOrderDateBefore(
                OrderStatus.PAYMENT_FAILED,
                LocalDateTime.now().minusWeeks(timeBeforeDeletingOrder)
        );

        if (ordersToCancel.isEmpty()) {
            stepContribution.setExitStatus(new ExitStatus(NO_ORDERS_TO_CANCEL));
            log.info("No orders to cancel");
        } else {
            final List<OrderEntity> successfullyCancelled = new java.util.ArrayList<>(ordersToCancel.size());
            for (OrderEntity order : ordersToCancel) {
                try {
                    stockService.releaseStock(order.getUuid());
                    order.setStatus(OrderStatus.CANCELED);
                    successfullyCancelled.add(order);
                } catch (Exception e) {
                    log.error("Failed to cancel order {} — skipping and continuing: {}", order.getUuid(), e.getMessage(), e);
                }
            }

            // Persist the CANCELED status: relying on JPA dirty-checking alone is unsafe across
            // tasklet/transaction boundaries — without an explicit save the rows can stay
            // PAYMENT_FAILED in the DB and the tasklet would re-process the same orders forever.
            orderRepository.saveAll(successfullyCancelled);

            final Map<String, List<UUID>> cancelledOrdersIdsByUserEmail = successfullyCancelled.stream()
                    .collect(Collectors.groupingBy(
                            co -> co.getUserEntity().getEmail(),
                            Collectors.mapping(BaseEntity::getUuid, Collectors.toList())
                    ));

            stepContribution.getStepExecution().getJobExecution().getExecutionContext()
                    .put(PENDING_ORDERS_MAP_BY_USER_EMAIL, cancelledOrdersIdsByUserEmail);
            stepContribution.setExitStatus(COMPLETED);
        }

        log.info("CancelAllPendingOrdersByTimeTasklet finished");

        return RepeatStatus.FINISHED;
    }
}
