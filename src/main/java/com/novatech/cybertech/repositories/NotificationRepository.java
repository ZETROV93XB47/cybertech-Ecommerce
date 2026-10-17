package com.novatech.cybertech.repositories;

import com.novatech.cybertech.entities.NotificationEntity;
import com.novatech.cybertech.entities.enums.NotificationStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationRepository extends CrudBaseRepository<NotificationEntity, Long> {

    /**
     * Returns rows that the Phase 3 batch tasklet
     * ({@link com.novatech.cybertech.batch.task.RedeliverFailedNotificationsTasklet})
     * should attempt to redeliver:
     *
     * <ul>
     *   <li>{@code status == PENDING_RETRY} — the in-process Resilience4j retry
     *       has been exhausted but the row is NOT yet terminal {@code FAILED}.</li>
     *   <li>{@code retryCount < maxAttempts} — under the cumulative redrive
     *       budget. Once the budget is spent the tasklet flips the row to
     *       terminal {@code FAILED} and stops picking it up.</li>
     *   <li>{@code lastAttemptAt IS NULL OR lastAttemptAt < threshold} —
     *       outside the per-row backoff window since the last attempt. Avoids
     *       hammering rows that just failed an in-process attempt seconds ago.</li>
     * </ul>
     *
     * <p>Ordered oldest-first ({@code lastAttemptAt ASC NULLS FIRST}) so the
     * tasklet always works on the most-stale rows when it caps at
     * {@code batch-size}.
     *
     * @param status      should always be {@link NotificationStatus#PENDING_RETRY}
     *                    (parameterized for testability rather than hard-coded
     *                    in the JPQL)
     * @param maxAttempts cumulative cap from {@code cybertech.notification.redelivery.max-attempts}
     * @param threshold   {@code now - cybertech.notification.redelivery.backoff-minutes}
     * @param pageable    constructed with {@link Pageable#ofSize(int)} from
     *                    {@code cybertech.notification.redelivery.batch-size};
     *                    sort is provided by the JPQL itself
     */
    @Query("""
            SELECT n FROM NotificationEntity n
             WHERE n.status = :status
               AND n.retryCount < :maxAttempts
               AND (n.lastAttemptAt IS NULL OR n.lastAttemptAt < :threshold)
             ORDER BY n.lastAttemptAt ASC NULLS FIRST
            """)
    List<NotificationEntity> findRedrivable(
            @Param("status") NotificationStatus status,
            @Param("maxAttempts") int maxAttempts,
            @Param("threshold") LocalDateTime threshold,
            Pageable pageable
    );
}
