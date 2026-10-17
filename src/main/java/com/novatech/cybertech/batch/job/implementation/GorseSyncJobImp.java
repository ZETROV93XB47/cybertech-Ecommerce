package com.novatech.cybertech.batch.job.implementation;

import com.novatech.cybertech.batch.job.core.GorseSyncJob;
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

import static com.novatech.cybertech.constants.CyberTechAppConstants.GORSE_SYNC_JOB;

/**
 * See {@link GorseSyncJob} for the contract's intent.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GorseSyncJobImp implements GorseSyncJob {

    @Value("${cybertech.gorse.sync.job.activated:true}")
    private boolean activated;

    @Qualifier(GORSE_SYNC_JOB)
    private final Job job;

    private final JobLauncher jobLauncher;

    @Override
    @Scheduled(cron = "${cybertech.gorse.sync.job.cron:0 */15 * * * *}", zone = "UTC")
    public void startJob() {
        if (!activated) {
            return;
        }
        try {
            final LocalDateTime now = LocalDateTime.now();
            jobLauncher.run(job, new JobParametersBuilder().addLocalDateTime("date", now).toJobParameters());
        } catch (JobInstanceAlreadyCompleteException | JobExecutionAlreadyRunningException | JobRestartException |
                 InvalidJobParametersException e) {
            log.error("Error launching Gorse sync job", e);
        }
    }
}
