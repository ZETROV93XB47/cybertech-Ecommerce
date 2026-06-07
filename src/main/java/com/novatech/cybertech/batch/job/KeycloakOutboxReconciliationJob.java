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

import static com.novatech.cybertech.constants.CyberTechAppConstants.KEYCLOAK_OUTBOX_RECONCILIATION_JOB;

/**
 * Cron scheduler for the Keycloak outbox reconciliation job (user-saga crash recovery).
 *
 * <p>The synchronous request path already performs every Keycloak effect immediately — this job
 * only drains rows a crash left {@code PENDING} (see
 * {@code docs/superpowers/specs/2026-06-04-keycloak-outbox-design.md}). A slow tick is therefore
 * fine: the goal is convergence, not latency.
 *
 * <p>Mirrors {@link RedeliverFailedNotificationsJob} structurally: one {@code @Scheduled}
 * dispatcher, one {@code activated} flag, one {@link Job} qualifier-injected by name, single
 * launcher exception barrier.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KeycloakOutboxReconciliationJob {

    @Value("${cybertech.keycloak.outbox.job.activated:true}")
    private boolean activated;

    @Qualifier(KEYCLOAK_OUTBOX_RECONCILIATION_JOB)
    private final Job job;

    private final JobLauncher jobLauncher;

    @Scheduled(cron = "${cybertech.keycloak.outbox.job.cron:0 */15 * * * *}", zone = "UTC")
    public void startJob() {
        if (!activated) {
            return;
        }
        try {
            final LocalDateTime now = LocalDateTime.now();
            jobLauncher.run(job, new JobParametersBuilder().addLocalDateTime("date", now).toJobParameters());
        } catch (JobInstanceAlreadyCompleteException | JobExecutionAlreadyRunningException | JobRestartException |
                 InvalidJobParametersException e) {
            log.error("Error launching keycloak outbox reconciliation job", e);
        }
    }
}
