package com.novatech.cybertech.batch.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
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

import static com.novatech.cybertech.constants.CyberTechAppConstants.REDELIVER_FAILED_NOTIFICATIONS_JOB;

/**
 * Cron scheduler for the Phase 3 notification redrive job.
 *
 * <p><b>Layered retry architecture:</b> in-process retries already happen
 * first via Resilience4j inside
 * {@link com.novatech.cybertech.services.implementation.NotificationRetryableDeliveryImp}
 * (3 attempts, exponential backoff, all within a few seconds). This batch
 * layer is the <em>cumulative</em> long-tail safety net that picks up rows
 * left in {@link com.novatech.cybertech.entities.enums.NotificationStatus#PENDING_RETRY}
 * after the in-process budget was spent — typical cause is an outage longer
 * than the in-process backoff window (mail server down, DNS hiccup, etc.).
 *
 * <p>Mirrors {@link StockCleanupJob} structurally: one {@code @Scheduled}
 * dispatcher, one {@code activated} flag, one {@link Job} qualifier-injected
 * by name, single launcher exception barrier.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedeliverFailedNotificationsJob {

    @Value("${cybertech.notification.redelivery.job.activated:true}")
    private boolean activated;

    @Qualifier(REDELIVER_FAILED_NOTIFICATIONS_JOB)
    private final Job job;

    private final JobLauncher jobLauncher;

    /**
     * Default cron: every 15 minutes. The in-process retry already covers
     * sub-second blips, so the batch can afford a slow tick — the goal is
     * to drain a backlog accumulated over a longer outage, not to react in
     * real time.
     */
    @Scheduled(cron = "${cybertech.notification.redelivery.job.cron:0 */15 * * * *}", zone = "UTC")
    public void startJob() {
        if (!activated) {
            return;
        }
        try {
            final LocalDateTime now = LocalDateTime.now();
            jobLauncher.run(job, new JobParametersBuilder().addLocalDateTime("date", now).toJobParameters());
        } catch (JobInstanceAlreadyCompleteException | JobExecutionAlreadyRunningException | JobRestartException |
                 InvalidJobParametersException e) {
            log.error("Error launching notification redelivery job", e);
        }
    }
}
