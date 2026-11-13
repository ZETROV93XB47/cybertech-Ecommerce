package com.novatech.cybertech.batch.task;

import com.novatech.cybertech.batch.base.BaseTasklet;
import com.novatech.cybertech.clients.GorseClient;
import com.novatech.cybertech.dto.request.gorse.GorseFeedbackDto;
import com.novatech.cybertech.dto.request.gorse.GorseItemDto;
import com.novatech.cybertech.dto.request.gorse.GorseUserDto;
import com.novatech.cybertech.entities.ProductEntity;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.entities.document.UserEvent;
import com.novatech.cybertech.mappers.gorse.GorseMapper;
import com.novatech.cybertech.repositories.ProductRepository;
import com.novatech.cybertech.repositories.UserEventRepository;
import com.novatech.cybertech.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Pushes the product catalog, the user base, and recent behavioral feedback ({@link UserEvent})
 * to Gorse so its offline recommenders have data to train on. Phase 1 of the Gorse integration:
 * ingestion only — reading Gorse's precomputed recommendations back (home page, email digest)
 * is a separate, later phase.
 *
 * <p>The three phases below are intentionally isolated by their own try/catch: a failure syncing
 * items must not prevent users or feedback from syncing in the same run, and vice-versa. This
 * mirrors the per-item try/catch already used in
 * {@link CancelAllPendingOrdersByTimeTasklet#execute}.
 *
 * <p>Feedback uses a sliding lookback window ({@code lookbackMinutes}, wider than the cron
 * interval as a safety margin) instead of a durable watermark: Gorse feedback is idempotent on
 * {@code (FeedbackType, UserId, ItemId)}, so re-sending the overlap between two runs is harmless.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GorseSyncTasklet extends BaseTasklet {

    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final UserEventRepository userEventRepository;
    private final GorseClient gorseClient;
    private final GorseMapper gorseMapper;

    @Value("${cybertech.gorse.sync.job.lookback-minutes:20}")
    private int lookbackMinutes;

    @Override
    @Transactional(readOnly = true)
    public RepeatStatus execute(final StepContribution stepContribution, final StepArguments stepArguments) {
        log.info("Starting GorseSyncTasklet");

        syncItems();
        syncUsers();
        syncFeedback();

        stepContribution.setExitStatus(ExitStatus.COMPLETED);
        log.info("GorseSyncTasklet finished");
        return RepeatStatus.FINISHED;
    }

    private void syncItems() {
        try {
            final List<ProductEntity> products = productRepository.findAll();
            if (products.isEmpty()) {
                return;
            }
            final List<GorseItemDto> items = products.stream().map(gorseMapper::toItemDto).toList();
            gorseClient.upsertItems(items);
        } catch (Exception e) {
            log.error("Failed to sync items to Gorse — will retry on the next scheduled run", e);
        }
    }

    private void syncUsers() {
        try {
            final List<UserEntity> users = userRepository.findAll();
            if (users.isEmpty()) {
                return;
            }
            final List<GorseUserDto> gorseUsers = users.stream().map(gorseMapper::toUserDto).toList();
            gorseClient.upsertUsers(gorseUsers);
        } catch (Exception e) {
            log.error("Failed to sync users to Gorse — will retry on the next scheduled run", e);
        }
    }

    private void syncFeedback() {
        try {
            final Instant windowEnd = Instant.now();
            final Instant windowStart = windowEnd.minus(Duration.ofMinutes(lookbackMinutes));

            final List<UserEvent> events = userEventRepository.findByTimestampBetween(windowStart, windowEnd);
            final List<GorseFeedbackDto> feedback = events.stream()
                    // Some event types (SEARCH_QUERY, BOUNCE, HOVER, ...) carry no productId —
                    // Gorse feedback requires both UserId and ItemId, so they're not eligible.
                    .filter(event -> event.getProductId() != null && !event.getProductId().isBlank())
                    .map(gorseMapper::toFeedbackDto)
                    .toList();

            if (feedback.isEmpty()) {
                return;
            }
            gorseClient.upsertFeedback(feedback);
        } catch (Exception e) {
            log.error("Failed to sync feedback to Gorse — will retry on the next scheduled run", e);
        }
    }
}
