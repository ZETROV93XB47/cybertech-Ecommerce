package com.novatech.cybertech.batch.job;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.JobRestartException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import static com.novatech.cybertech.constants.CyberTechAppConstants.REPORT_FAILED_PAYMENT_AND_CANCELLED_ORDERS_JOB;

@Slf4j
@Service
@RequiredArgsConstructor
public class CybertechOrdersUpdateJob {

    @Value("${cybertech.orders.update.job.activated}")
    private boolean activated;

    @Qualifier(REPORT_FAILED_PAYMENT_AND_CANCELLED_ORDERS_JOB)
    private final Job job;

    private final JobLauncher jobLauncher;


    @Scheduled(cron = "${cybertech.orders.update.job.cron}", zone = "UTC")
    public JobExecution startJob() {
        log.debug("Starting " + REPORT_FAILED_PAYMENT_AND_CANCELLED_ORDERS_JOB + " Job with parameters: ");

        try {
            if (activated) {
                final LocalDateTime now = LocalDateTime.now();
                return jobLauncher.run(job, new JobParametersBuilder().addLocalDateTime("runDate", now).toJobParameters());
            }

            log.info("REPORT_FAILED_PAYMENT_AND_CANCELLED_ORDERS_JOB is not scheduled, the Job won't be executed.");
            return null;
        } catch (JobInstanceAlreadyCompleteException | JobExecutionAlreadyRunningException | JobRestartException |
                 InvalidJobParametersException e) {

            log.error("An Error occurred during {} launching: \n{} ", REPORT_FAILED_PAYMENT_AND_CANCELLED_ORDERS_JOB, e.getMessage());
            return null;
        }
    }
}
