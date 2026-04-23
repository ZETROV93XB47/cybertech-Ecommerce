package com.novatech.cybertech.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * Persistent ledger of every Stripe webhook event the application has already processed.
 *
 * <p>Stripe guarantees <em>at-least-once</em> delivery for webhooks: any non-2xx response — or a
 * timeout — causes the event to be re-delivered, and Stripe may also send the same event multiple
 * times during their replay drills. To make {@code PaymentWebhookServiceImp.handleEvent} idempotent
 * we record each {@code event.getId()} (e.g. {@code "evt_1NxYz..."}) the first time we successfully
 * process it. A subsequent delivery of the same event short-circuits before any side-effect is
 * executed (no DB mutation, no domain event re-published).
 *
 * <p>The {@code stripeEventId} column carries a {@code UNIQUE} constraint so that even if two
 * concurrent deliveries race past the {@code existsByStripeEventId} pre-check, the database itself
 * rejects the duplicate insert (race-safe second line of defence).
 *
 * @see com.novatech.cybertech.repositories.ProcessedWebhookEventRepository
 * @see com.novatech.cybertech.services.implementation.PaymentWebhookServiceImp
 */
@Entity
@Setter
@Getter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true)
@Table(
        name = "processedWebhookEvent",
        uniqueConstraints = @UniqueConstraint(name = "uk_processed_webhook_event_stripeEventId", columnNames = "stripeEventId")
)
public class ProcessedWebhookEventEntity extends BaseEntity<Long> {

    /**
     * The opaque Stripe event identifier (e.g. {@code "evt_1NxYz..."}). Carries a UNIQUE constraint
     * so concurrent inserts of the same event are rejected at the DB layer, not just by the
     * application-level pre-check.
     */
    @Column(name = "stripeEventId", nullable = false, unique = true, length = 128)
    private String stripeEventId;

    /**
     * UTC wall-clock time at which the event was successfully processed. Used for forensic/auditing
     * purposes — never participates in business logic.
     */
    @Column(name = "processedAt", nullable = false)
    private LocalDateTime processedAt;
}
