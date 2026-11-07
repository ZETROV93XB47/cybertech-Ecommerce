package com.novatech.cybertech.batch.task;

import com.novatech.cybertech.batch.base.BaseTasklet;
import com.novatech.cybertech.dto.data.NotificationContext;
import com.novatech.cybertech.dto.data.RecommendationDigestPayload;
import com.novatech.cybertech.repositories.UserRepository;
import com.novatech.cybertech.services.core.NotificationRetryableDelivery;
import com.novatech.cybertech.services.core.RecommendationDigestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Sends the daily product-suggestion e-mail to every active user who did not unsubscribe.
 *
 * <p>Users are read by pages of ids ({@code cybertech.recommendation.digest.batch-size}), each
 * page starting after the last id of the previous one. Per user:
 * {@link RecommendationDigestService#prepareDigest} picks the products and records them as sent
 * in its own transaction, then {@link NotificationRetryableDelivery#deliver} sends the e-mail
 * (retries, audit row, redelivery by {@code REDELIVER_FAILED_NOTIFICATIONS_JOB}). A failure for
 * one user is logged and the job moves on, as in {@link CancelAllPendingOrdersByTimeTasklet}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationDigestTasklet extends BaseTasklet {

    private static final long FIRST_ID = 0L;

    private final UserRepository userRepository;
    private final RecommendationDigestService recommendationDigestService;
    private final NotificationRetryableDelivery retryableDelivery;

    @Value("${cybertech.recommendation.digest.batch-size:100}")
    private int batchSize;

    @Override
    public RepeatStatus execute(final StepContribution stepContribution, final StepArguments stepArguments) {
        log.info("Starting RecommendationDigestTasklet");
        int sent = 0;
        int failed = 0;
        List<Long> page = nextPage(FIRST_ID);
        while (!page.isEmpty()) {
            for (final Long userId : page) {
                try {
                    if (sendTo(userId)) {
                        sent++;
                    }
                } catch (Exception e) {
                    failed++;
                    log.error("Suggestion e-mail failed for user id {}, moving on to the next user", userId, e);
                }
            }
            page = nextPage(page.getLast());
        }
        stepContribution.setExitStatus(ExitStatus.COMPLETED);
        log.info("RecommendationDigestTasklet finished: {} e-mail(s) sent, {} failure(s)", sent, failed);
        return RepeatStatus.FINISHED;
    }

    private List<Long> nextPage(final long afterId) {
        return userRepository.findRecommendationEmailRecipientIdsAfter(afterId, PageRequest.of(0, batchSize));
    }

    private boolean sendTo(final Long userId) {
        final Optional<NotificationContext<RecommendationDigestPayload>> context = recommendationDigestService.prepareDigest(userId);
        context.ifPresent(retryableDelivery::deliver);
        return context.isPresent();
    }
}
