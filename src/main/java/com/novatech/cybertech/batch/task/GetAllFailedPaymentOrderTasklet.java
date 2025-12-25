package com.novatech.cybertech.batch.task;

import com.novatech.cybertech.batch.base.BaseTasklet;
import com.novatech.cybertech.entities.BaseEntity;
import com.novatech.cybertech.entities.PaymentEntity;
import com.novatech.cybertech.entities.enums.PaymentStatus;
import com.novatech.cybertech.repositories.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.stream.Collectors;

import static com.novatech.cybertech.constants.CyberTechAppConstants.FAILED_PAYMENT_ORDERS_MAP_BY_USERS;
import static com.novatech.cybertech.constants.CyberTechAppConstants.NO_FAILED_PAYMENT_ORDER_FOUND;
import static org.springframework.batch.core.ExitStatus.COMPLETED;


@Slf4j
@Service
@RequiredArgsConstructor
public class GetAllFailedPaymentOrderTasklet extends BaseTasklet {

    private final PaymentRepository paymentRepository;


    @Override
    @Transactional
    public RepeatStatus execute(final StepContribution stepContribution, final StepArguments stepArguments) {

        final Map<String, Long> failedPaymentsMapUserEmailByOrderId = paymentRepository.findByPaymentStatus(PaymentStatus.FAILED).stream()
                .map(PaymentEntity::getOrderEntity)
                .collect(Collectors.toMap(o -> o.getUserEntity().getEmail(), BaseEntity::getId));

        if (failedPaymentsMapUserEmailByOrderId.isEmpty()) {
            stepContribution.setExitStatus(new ExitStatus(NO_FAILED_PAYMENT_ORDER_FOUND));
            log.info("No failed payment orders found");
        }

        else {
            stepContribution.getStepExecution().getJobExecution().getExecutionContext().put(FAILED_PAYMENT_ORDERS_MAP_BY_USERS, failedPaymentsMapUserEmailByOrderId);
            stepContribution.setExitStatus(COMPLETED);
        }

        return RepeatStatus.FINISHED;
    }
}
