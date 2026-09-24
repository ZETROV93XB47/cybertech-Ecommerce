package com.novatech.cybertech.batch.task;

import com.novatech.cybertech.batch.base.BaseTasklet;
import com.novatech.cybertech.dto.data.NotificationContext;
import com.novatech.cybertech.dto.data.NotificationRedrivePayload;
import com.novatech.cybertech.entities.NotificationEntity;
import com.novatech.cybertech.entities.enums.NotificationStatus;
import com.novatech.cybertech.repositories.NotificationRepository;
import com.novatech.cybertech.services.core.NotificationRetryableDelivery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Phase 3 long-tail safety net for the notification pipeline.
 *
 * <p>Periodically scans {@link NotificationEntity} rows in status
 * {@link NotificationStatus#PENDING_RETRY} (left there by the Phase 2
 * Resilience4j {@code @Retry} fallback in
 * {@link com.novatech.cybertech.services.implementation.NotificationRetryableDeliveryImp}),
 * rebuilds a fresh {@link NotificationContext} from the persisted
 * {@link NotificationEntity#getPayload() payload} JSON, and resubmits the
 * notification through the SAME retry-protected bean used by the in-process
 * listener path. After the cumulative budget is spent the row is promoted to
 * terminal {@link NotificationStatus#FAILED}.
 *
 * <h2>One row per notification</h2>
 *
 * Each redrive tick calls {@link NotificationRetryableDelivery#redeliver(NotificationContext, NotificationEntity)}
 * on the SAME {@link NotificationEntity} row picked up from {@code findRedrivable} — the row is
 * updated in place ({@code status}, {@code retryCount}, {@code lastAttemptAt}, {@code errorMessage})
 * rather than a new row being inserted per attempt. Only the latest error message is kept, no
 * per-attempt history — a deliberate simplification: an earlier design inserted a fresh row on
 * every redrive, which meant that fresh row was itself indistinguishable from a genuine
 * coordination row and became independently eligible for its own redrive lineage on the next
 * tick. During a sustained outage this forked an unbounded number of parallel lineages for a
 * single logical notification, risking multiple duplicate sends once the outage cleared.
 *
 * <p>After each redrive, {@code entity.retryCount} is bumped by the in-process {@code max-attempts}
 * (3 by default — the Resilience4j budget consumed by the just-finished {@code redeliver} call).
 * If the bumped count crosses the cumulative cap, the row is promoted to
 * {@link NotificationStatus#FAILED} (terminal); else it stays {@link NotificationStatus#PENDING_RETRY}
 * for another tick.
 *
 * <h2>Reuse of the retryable bean</h2>
 *
 * The tasklet does NOT call the dispatcher directly. Going through
 * {@link NotificationRetryableDelivery} means each redrive tick gets a fresh
 * Resilience4j retry budget (3 attempts with exponential backoff) before
 * returning to the tasklet — exactly the same code path as the synchronous
 * listener flow. One source of truth for retry semantics.
 *
 * <p>Mirrors the structure of
 * {@link CleanUpExpiredStockReservationsTasklet} (one-shot tasklet,
 * {@code @Transactional}, sets {@link ExitStatus#COMPLETED} and returns
 * {@link RepeatStatus#FINISHED}). See {@link BaseTasklet} for the
 * {@code ScopedValue}-driven {@code execute(StepContribution, StepArguments)}
 * contract.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedeliverFailedNotificationsTasklet extends BaseTasklet {

    private final NotificationRepository notificationRepository;
    private final NotificationRetryableDelivery retryableDelivery;
    private final ObjectMapper objectMapper;

    /**
     * Cumulative cap on attempts across all redrive ticks for a single row.
     * Once {@code entity.retryCount >= maxAttempts} the tasklet promotes the
     * row to terminal {@link NotificationStatus#FAILED} instead of redriving.
     */
    @Value("${cybertech.notification.redelivery.max-attempts:9}")
    private int cumulativeMaxAttempts;

    /**
     * Per-row backoff window: only redrive rows whose {@code lastAttemptAt} is
     * older than {@code now - backoffMinutes}. Avoids hammering a row that
     * just exited its in-process retry seconds ago.
     */
    @Value("${cybertech.notification.redelivery.backoff-minutes:10}")
    private long backoffMinutes;

    /**
     * Defensive cap on rows processed per tick — a long outage that produced
     * tens of thousands of {@code PENDING_RETRY} rows should drain across many
     * ticks rather than blow up a single batch run.
     */
    @Value("${cybertech.notification.redelivery.batch-size:50}")
    private int batchSize;

    /**
     * One redrive tick. See class javadoc for the one-row-per-notification update pattern.
     *
     * @return {@link RepeatStatus#FINISHED} — one-shot per scheduled run
     */
    @Override
    @Transactional
    public RepeatStatus execute(final StepContribution stepContribution, final StepArguments stepArguments) {
        log.info("Starting RedeliverFailedNotificationsTasklet");

        final LocalDateTime threshold = LocalDateTime.now().minusMinutes(backoffMinutes);

        final List<NotificationEntity> candidates = notificationRepository.findRedrivable(
                NotificationStatus.PENDING_RETRY,
                cumulativeMaxAttempts,
                threshold,
                PageRequest.ofSize(batchSize)
        );

        if (candidates.isEmpty()) {
            log.info("No PENDING_RETRY notifications to redrive");
            stepContribution.setExitStatus(ExitStatus.COMPLETED);
            return RepeatStatus.FINISHED;
        }

        log.info("Found {} PENDING_RETRY notifications to redrive", candidates.size());

        int redelivered = 0;
        int corrupted = 0;

        for (final NotificationEntity entity : candidates) {
            // Step 1: try to rebuild the dispatch context from the persisted
            // redrive payload. If the payload was lost (null) or unparseable,
            // there is literally nothing to redrive — flip the row to terminal
            // FAILED and continue. The in-process retry-eligible bean is NOT
            // invoked in that branch.
            final NotificationContext<?> context;
            try {
                context = rebuildContext(entity);
            } catch (Exception e) {
                log.warn("Notification {} has a missing/corrupted redrive payload; promoting to FAILED",
                        entity.getId(), e);
                markCorrupted(entity);
                corrupted++;
                continue;
            }

            // Step 2: resubmit through the SAME @Retry-protected bean used by the synchronous
            // listener path, updating `entity` in place (SENT, or PENDING_RETRY with retryCount
            // bumped) — see class javadoc for why this is a single row, not a new one per attempt.
            try {
                retryableDelivery.redeliver(context, entity);
            } catch (Exception e) {
                // The retryable bean is contractually swallow-on-exhaustion
                // (see NotificationRetryableDeliveryImp javadoc). Anything
                // escaping here is a true infrastructure failure (DB/proxy
                // glitch, etc.). Log and treat the redrive as a no-op so the
                // promotion check below still runs against whatever state
                // `entity` was last successfully saved in.
                log.error("Unexpected escape from retryableDelivery.redeliver() for notification {}",
                        entity.getId(), e);
            }

            // Step 3: redeliver() already updated + saved `entity` in place. Promote to terminal
            // FAILED only if it's still not sent and the cumulative budget across all ticks is
            // now exhausted — a row that just succeeded (status flipped to SENT) is left alone.
            if (entity.getStatus() == NotificationStatus.PENDING_RETRY && entity.getRetryCount() >= cumulativeMaxAttempts) {
                entity.setStatus(NotificationStatus.FAILED);
                notificationRepository.save(entity);
                log.warn("Notification {} promoted to terminal FAILED after cumulative {} attempts",
                        entity.getId(), entity.getRetryCount());
            }
            redelivered++;
        }

        log.info("RedeliverFailedNotificationsTasklet finished: redelivered={}, corrupted={}, total={}",
                redelivered, corrupted, candidates.size());
        stepContribution.setExitStatus(ExitStatus.COMPLETED);
        return RepeatStatus.FINISHED;
    }

    /**
     * Deserialize the {@code payload} column into a
     * {@link NotificationRedrivePayload} and rebuild a fresh
     * {@link NotificationContext}.
     *
     * @throws IllegalStateException if the payload column is null
     * @throws RuntimeException      if Jackson deserialization fails
     */
    private NotificationContext<?> rebuildContext(final NotificationEntity entity) {
        final String payloadJson = entity.getPayload();
        if (payloadJson == null || payloadJson.isBlank()) {
            throw new IllegalStateException("payload column is null/blank — nothing to redrive");
        }
        final NotificationRedrivePayload snapshot;
        try {
            snapshot = objectMapper.readValue(payloadJson, NotificationRedrivePayload.class);
        } catch (Exception e) {
            // Wrap so the caller can treat it uniformly with the null branch.
            throw new RuntimeException("failed to deserialize NotificationRedrivePayload", e);
        }
        if (snapshot == null) {
            throw new IllegalStateException("deserialized NotificationRedrivePayload is null");
        }
        return snapshot.toNotificationContext();
    }

    /**
     * Mark a row as terminal {@link NotificationStatus#FAILED} because we
     * cannot reconstruct a context for it. The retryable bean is NOT invoked
     * in this branch.
     */
    private void markCorrupted(final NotificationEntity entity) {
        entity.setStatus(NotificationStatus.FAILED);
        entity.setLastAttemptAt(LocalDateTime.now());
        entity.setErrorHistory(List.of("redrive payload missing/corrupted"));
        notificationRepository.save(entity);
    }
}
