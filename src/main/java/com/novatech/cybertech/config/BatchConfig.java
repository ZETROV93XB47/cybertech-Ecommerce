package com.novatech.cybertech.config;

import com.novatech.cybertech.batch.task.CancelAllPendingOrdersByTimeTasklet;
import com.novatech.cybertech.batch.task.GetAllFailedPaymentOrderTasklet;
import com.novatech.cybertech.batch.task.OrdersSummaryReportListener;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import static com.novatech.cybertech.constants.CyberTechAppConstants.REPORT_FAILED_PAYMENT_AND_CANCELLED_ORDERS_JOB;

@Configuration
@RequiredArgsConstructor
public class BatchConfig {

    private static final String GET_FAILED_PAYMENT_ORDERS = "GET_FAILED_PAYMENT_ORDERS";
    private static final String CANCEL_ALL_PENDING_ORDERS_BY_TIME_TASKLET = "CancelAllPendingOrdersByTimeTasklet";
    private static final String GET_ALL_FAILED_PAYMENTS_ORDER_TASKLET = "GetAllFailedPaymentOrderTasklet";

    private final JobRepository jobRepository;
    private final PlatformTransactionManager platformTransactionManager;

    private final OrdersSummaryReportListener ordersSummaryReportListener;
    private final GetAllFailedPaymentOrderTasklet getAllFailedPaymentOrderTasklet;
    private final CancelAllPendingOrdersByTimeTasklet cancelAllPendingOrdersByTimeTasklet;


    @Bean(GET_FAILED_PAYMENT_ORDERS)
    public Job reportFailedOrders() {

        final Step getAllFailedPaymentOrderStep = getAllFailedPaymentOrder();
        final Step cancelAllPendingOrdersByTime = cancelAllPendingOrdersByTime();

        return new JobBuilder(REPORT_FAILED_PAYMENT_AND_CANCELLED_ORDERS_JOB, jobRepository)
                .start(getAllFailedPaymentOrderStep)
                .next(cancelAllPendingOrdersByTime)
                .listener(ordersSummaryReportListener)
                .build();
    }

    @Bean(GET_ALL_FAILED_PAYMENTS_ORDER_TASKLET)
    public Step getAllFailedPaymentOrder() {
        return new StepBuilder(GET_ALL_FAILED_PAYMENTS_ORDER_TASKLET, jobRepository)
                .tasklet(getAllFailedPaymentOrderTasklet, platformTransactionManager)
                .allowStartIfComplete(true)
                .build();
    }

    @Bean(CANCEL_ALL_PENDING_ORDERS_BY_TIME_TASKLET)
    public Step cancelAllPendingOrdersByTime() {
        return new StepBuilder(CANCEL_ALL_PENDING_ORDERS_BY_TIME_TASKLET, jobRepository)
                .tasklet(cancelAllPendingOrdersByTimeTasklet, platformTransactionManager)
                .allowStartIfComplete(true)
                .build();
    }
}
