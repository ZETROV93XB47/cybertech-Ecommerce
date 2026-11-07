package com.novatech.cybertech.batch.job.implementation;

import com.novatech.cybertech.batch.job.core.StockAlertJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
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

import static com.novatech.cybertech.constants.CyberTechAppConstants.STOCK_ALERT_JOB;

/**
 * See {@link StockAlertJob} for the contract's intent. On by default: the user asked for the
 * alert, so a local stack sends it too (the e-mails land in Mailpit).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockAlertJobImp implements StockAlertJob {

    @Value("${cybertech.stock-alert.job.activated:true}")
    private boolean activated;

    @Qualifier(STOCK_ALERT_JOB)
    private final Job job;

    private final JobLauncher jobLauncher;

    // Two pods must not both send the same alerts (see RedisConfig#lockProvider). The PENDING
    // re-check of StockAlertServiceImp#prepareAlert is the second guard.
    @Override
    @Scheduled(cron = "${cybertech.stock-alert.job.cron:0 */10 * * * *}", zone = "UTC")
    @SchedulerLock(name = "stockAlertJob", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void startJob() {
        if (!activated) {
            return;
        }
        try {
            final LocalDateTime now = LocalDateTime.now();
            jobLauncher.run(job, new JobParametersBuilder().addLocalDateTime("date", now).toJobParameters());
        } catch (JobInstanceAlreadyCompleteException | JobExecutionAlreadyRunningException | JobRestartException |
                 InvalidJobParametersException e) {
            log.error("Error launching stock alert job", e);
        }
    }
}
