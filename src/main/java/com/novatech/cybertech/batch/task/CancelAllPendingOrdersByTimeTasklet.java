package com.novatech.cybertech.batch.task;

import com.novatech.cybertech.batch.base.BaseTasklet;
import com.novatech.cybertech.entities.BaseEntity;
import com.novatech.cybertech.entities.OrderEntity;
import com.novatech.cybertech.entities.enums.OrderStatus;
import com.novatech.cybertech.repositories.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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


    @Override
    @Transactional
    public RepeatStatus execute(final StepContribution stepContribution, final StepArguments stepArguments) {

        final List<OrderEntity> ordersToCancel = orderRepository.findByStatusAndOrderDateBefore(OrderStatus.PENDING_PAYMENT, LocalDateTime.now().minusWeeks(timeBeforeDeletingOrder))
                .stream()
                .map(orderEntity -> {
                    orderEntity.setStatus(OrderStatus.CANCELED);
                    return orderEntity;
                }).toList();

        if (ordersToCancel.isEmpty()) {
            stepContribution.setExitStatus(new ExitStatus(NO_ORDERS_TO_CANCEL));
            log.info("No orders to cancel");
        }

        else {
            final List<OrderEntity> cancelledOrders = orderRepository.saveAll(ordersToCancel);
            final Map<String, Long> cancelledOrdersIdsByUserEmail = cancelledOrders.stream().collect(Collectors.toMap(o -> o.getUserEntity().getEmail(), BaseEntity::getId));

            stepContribution.getStepExecution().getJobExecution().getExecutionContext().put(PENDING_ORDERS_MAP_BY_USER_EMAIL, cancelledOrdersIdsByUserEmail);
            stepContribution.setExitStatus(COMPLETED);
        }

        return RepeatStatus.FINISHED;
    }
}
