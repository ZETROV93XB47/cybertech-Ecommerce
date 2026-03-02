package com.novatech.cybertech.config;

import com.novatech.cybertech.batch.task.CancelAllPendingOrdersByTimeTasklet;
import com.novatech.cybertech.batch.task.GetAllFailedPaymentOrderTasklet;
import com.novatech.cybertech.batch.task.CleanUpExpiredStockReservationsTasklet;
import com.novatech.cybertech.batch.task.ShipAllPaidOrdersTasklet;
import com.novatech.cybertech.batch.task.OrdersSummaryReportListener;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.transaction.PlatformTransactionManager;

import static com.novatech.cybertech.constants.CyberTechAppConstants.CLEAN_UP_EXPIRED_STOCK_JOB;
import static com.novatech.cybertech.constants.CyberTechAppConstants.REPORT_FAILED_PAYMENT_AND_CANCELLED_ORDERS_JOB;

@Configuration
@RequiredArgsConstructor
public class BatchConfig {

    private static final String GET_FAILED_PAYMENT_ORDERS = "GET_FAILED_PAYMENT_ORDERS";
    private static final String CANCEL_ALL_PENDING_ORDERS_BY_TIME_TASKLET = "CancelAllPendingOrdersByTimeTasklet";
    private static final String GET_ALL_FAILED_PAYMENTS_ORDER_TASKLET = "GetAllFailedPaymentOrderTasklet";
    private static final String SHIP_ALL_PAID_ORDERS_TASKLET = "ShipAllAwaitingShippingOrdersTasklet";
    private static final String CLEAN_UP_EXPIRED_STOCK_RESERVATIONS_TASKLET = "CleanUpExpiredStockReservationsTasklet";

    private final JobRepository jobRepository;
    private final PlatformTransactionManager platformTransactionManager;

    private final OrdersSummaryReportListener ordersSummaryReportListener;
    private final GetAllFailedPaymentOrderTasklet getAllFailedPaymentOrderTasklet;
    private final CancelAllPendingOrdersByTimeTasklet cancelAllPendingOrdersByTimeTasklet;
    private final ShipAllPaidOrdersTasklet shipAllPaidOrdersTasklet;
    private final CleanUpExpiredStockReservationsTasklet cleanUpExpiredStockReservationsTasklet;


    @Primary
    @Bean(GET_FAILED_PAYMENT_ORDERS)
    public Job reportFailedOrders() {

        final Step getAllFailedPaymentOrderStep = getAllFailedPaymentOrder();
        final Step cancelAllPendingOrdersByTime = cancelAllPendingOrdersByTime();
        final Step shipAllAwaitingShippingOrders = shipAllAwaitingShippingOrders();

        return new JobBuilder(REPORT_FAILED_PAYMENT_AND_CANCELLED_ORDERS_JOB, jobRepository)
                .start(getAllFailedPaymentOrderStep)
                .next(cancelAllPendingOrdersByTime)
                .next(shipAllAwaitingShippingOrders)
                .listener(ordersSummaryReportListener)
                .build();
    }


    @Bean(CLEAN_UP_EXPIRED_STOCK_JOB)
    public Job cleanUpExpiredStockJob() {
        return new JobBuilder(CLEAN_UP_EXPIRED_STOCK_JOB, jobRepository)
                .start(cleanUpExpiredStockReservationsStep())
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

    @Bean(SHIP_ALL_PAID_ORDERS_TASKLET)
    public Step shipAllAwaitingShippingOrders() {
        return new StepBuilder(SHIP_ALL_PAID_ORDERS_TASKLET, jobRepository)
                .tasklet(shipAllPaidOrdersTasklet, platformTransactionManager)
                .allowStartIfComplete(true)
                .build();
    }

    @Bean(CLEAN_UP_EXPIRED_STOCK_RESERVATIONS_TASKLET)
    public Step cleanUpExpiredStockReservationsStep() {
        return new StepBuilder(CLEAN_UP_EXPIRED_STOCK_RESERVATIONS_TASKLET, jobRepository)
                .tasklet(cleanUpExpiredStockReservationsTasklet, platformTransactionManager)
                .allowStartIfComplete(true)
                .build();
    }

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1); // un seul thread = une seule exécution
        scheduler.setThreadNamePrefix("scheduler-");
        return scheduler;
    }


}
