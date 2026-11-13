package com.novatech.cybertech.entities.enums;

/**
 * Type of Keycloak side-effect recorded in the {@code keycloak_outbox} table.
 *
 * <ul>
 *   <li>{@link #CREATE} — reconciled by email lookup + compensation (the job NEVER re-creates:
 *       that would require the raw password, which is never stored at rest).</li>
 *   <li>{@link #UPDATE} — reconciled by idempotent re-application of the persisted payload
 *       to both Keycloak and the local DB (forward recovery — no secret needed).</li>
 *   <li>{@link #DELETE} — reconciled by re-issuing the idempotent (404-tolerant) delete.</li>
 * </ul>
 */
public enum OutboxOperationType {
    CREATE,
    UPDATE,
    DELETE
}
