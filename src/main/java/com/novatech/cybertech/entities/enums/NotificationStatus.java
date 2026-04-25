package com.novatech.cybertech.entities.enums;

/**
 * Lifecycle states for a {@link com.novatech.cybertech.entities.NotificationEntity}.
 *
 * <pre>
 *   PENDING ──(in-process retries)──► PENDING_RETRY ──(batch redrive)──► SENT
 *      │                                    │
 *      │                                    └──(maxAttempts exceeded)──► FAILED (terminal)
 *      │
 *      └──(immediate success)──► SENT
 * </pre>
 *
 * <ul>
 *   <li>{@link #PENDING} — reserved for future atomic-outbox use; row written
 *       inside the originating transaction <em>before</em> dispatch is
 *       attempted. Phase 1 does NOT write rows in this state.</li>
 *   <li>{@link #SENT} — terminal success. The transport accepted the message
 *       on at least one attempt.</li>
 *   <li>{@link #PENDING_RETRY} — in-process retries (Resilience4j {@code @Retry}
 *       in Phase 2) have been exhausted, but the global retry budget
 *       ({@code retryCount < maxAttempts}) still has room. Awaiting the
 *       Phase 3 batch redrive tasklet, which will flip the row to
 *       {@link #SENT} on success or {@link #FAILED} once the budget runs out.</li>
 *   <li>{@link #FAILED} — terminal dead-letter. {@code retryCount >= maxAttempts}
 *       and the redrive tasklet has given up. Operator action required.</li>
 * </ul>
 */
public enum NotificationStatus {
    PENDING,
    SENT,
    PENDING_RETRY,
    FAILED
}
