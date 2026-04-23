package com.novatech.cybertech.batch.task;

import com.novatech.cybertech.batch.base.BaseTasklet;
import com.novatech.cybertech.entities.StockEntity;
import com.novatech.cybertech.entities.enums.ReservationStatus;
import com.novatech.cybertech.repositories.StockRepository;
import com.novatech.cybertech.services.core.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Spring Batch tasklet that scans for stuck {@link ReservationStatus#ACTIVE} reservations older
 * than 15 minutes and releases them. Used as a safety-net behind the
 * {@link com.novatech.cybertech.listener.RedisExpirationListener} TTL flow: covers the case
 * where Redis loses the keyspace event (restart, network partition, listener crash) so a stuck
 * reservation cannot indefinitely pin product inventory.
 *
 * <p>BUG-110 (FIXED in F2): the tasklet used to load every reservation via
 * {@code stockRepository.findAll()} and filter in memory; it now uses the dedicated
 * {@link StockRepository#findByReservationStatusAndCreatedAtBefore} query for a tight
 * server-side filter.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CleanUpExpiredStockReservationsTasklet extends BaseTasklet {

    private final StockRepository stockRepository;
    private final StockService stockService;

    /**
     * Runs the cleanup: queries ACTIVE reservations older than the 15-minute threshold and
     * fires {@link StockService#releaseStock(UUID)} for each distinct order UUID found.
     * {@code releaseStock} is idempotent so collapsing on UUID is safe even if a single order
     * has multiple per-product reservations.
     *
     * @return {@link RepeatStatus#FINISHED} — the tasklet is one-shot per scheduled run
     */
    @Override
    @Transactional
    public RepeatStatus execute(StepContribution stepContribution, StepArguments stepArguments) {
        log.info("Starting CleanUpExpiredStockReservationsTasklet");

        final LocalDateTime threshold = LocalDateTime.now().minusMinutes(15);

        final List<StockEntity> expired = stockRepository
                .findByReservationStatusAndCreatedAtBefore(ReservationStatus.ACTIVE, threshold);

        final Set<UUID> expiredOrderUuids = expired.stream()
                .map(StockEntity::getOrderUuid)
                .collect(Collectors.toSet());

        if (expiredOrderUuids.isEmpty()) {
            log.info("No expired stock reservations found to clean up.");
            stepContribution.setExitStatus(ExitStatus.COMPLETED);
            return RepeatStatus.FINISHED;
        }

        log.info("Found {} orders with stuck reservations. Releasing stock...", expiredOrderUuids.size());

        for (UUID orderUuid : expiredOrderUuids) {
            // releaseStock handles DB row cleanup AND the Redis sentinel deletion.
            stockService.releaseStock(orderUuid);
        }

        stepContribution.setExitStatus(ExitStatus.COMPLETED);
        log.info("CleanUpExpiredStockReservationsTasklet finished");
        return RepeatStatus.FINISHED;
    }
}
