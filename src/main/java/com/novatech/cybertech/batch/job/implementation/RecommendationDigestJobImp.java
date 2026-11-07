package com.novatech.cybertech.batch.job.implementation;

import com.novatech.cybertech.batch.job.core.RecommendationDigestJob;
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

import static com.novatech.cybertech.constants.CyberTechAppConstants.RECOMMENDATION_DIGEST_JOB;

/**
 * See {@link RecommendationDigestJob} for the contract's intent. Off unless
 * {@code cybertech.recommendation.digest.job.activated} is true, so a local stack never e-mails
 * its test users unasked.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationDigestJobImp implements RecommendationDigestJob {

    @Value("${cybertech.recommendation.digest.job.activated:false}")
    private boolean activated;

    @Qualifier(RECOMMENDATION_DIGEST_JOB)
    private final Job job;

    private final JobLauncher jobLauncher;

    // Two pods must not both e-mail every user (see RedisConfig#lockProvider). The per-user
    // "already sent recently" check of RecommendationDigestServiceImp is the second guard.
    @Override
    @Scheduled(cron = "${cybertech.recommendation.digest.job.cron:0 0 10 * * *}", zone = "Europe/Paris")
    @SchedulerLock(name = "recommendationDigestJob", lockAtMostFor = "PT2H", lockAtLeastFor = "PT5M")
    public void startJob() {
        if (!activated) {
            return;
        }
        try {
            final LocalDateTime now = LocalDateTime.now();
            jobLauncher.run(job, new JobParametersBuilder().addLocalDateTime("date", now).toJobParameters());
        } catch (JobInstanceAlreadyCompleteException | JobExecutionAlreadyRunningException | JobRestartException |
                 InvalidJobParametersException e) {
            log.error("Error launching recommendation digest job", e);
        }
    }
}
