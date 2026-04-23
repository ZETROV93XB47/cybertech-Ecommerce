package com.novatech.cybertech.repositories;

import com.novatech.cybertech.entities.ProcessedWebhookEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring-Data repository for {@link ProcessedWebhookEventEntity}, the dedup-ledger backing the
 * Stripe webhook idempotency check (BUG-170 fix).
 *
 * <p>The single source of dedup truth is the unique constraint on
 * {@link ProcessedWebhookEventEntity#getStripeEventId()}. {@link #existsByStripeEventId(String)} is
 * the cheap fast-path used by {@code PaymentWebhookServiceImp.handleEvent} before any side-effect
 * runs; the DB constraint catches concurrent races that beat the existence check.
 */
@Repository
public interface ProcessedWebhookEventRepository extends JpaRepository<ProcessedWebhookEventEntity, Long> {

    /**
     * Cheap pre-check: returns {@code true} if the given Stripe event id has already been processed.
     *
     * @param stripeEventId the {@code event.getId()} returned by the Stripe SDK (e.g.
     *                      {@code "evt_1NxYz..."})
     * @return {@code true} if a {@link ProcessedWebhookEventEntity} row already exists for the id
     */
    boolean existsByStripeEventId(String stripeEventId);

    /**
     * Optional lookup of a previously-processed event row, used for diagnostics / audit log
     * read-back. Not on the hot path.
     *
     * @param stripeEventId the {@code event.getId()} returned by the Stripe SDK
     * @return the persisted ledger row, if any
     */
    Optional<ProcessedWebhookEventEntity> findByStripeEventId(String stripeEventId);
}
