package com.novatech.cybertech.entities.enums;

/**
 * Lifecycle of a {@code keycloak_outbox} row.
 *
 * <p>{@link #PENDING} rows older than the staleness window are crash leftovers picked up by the
 * reconciliation job. {@link #FAILED} rows (terminal, after the attempts cap or a clean abort)
 * are the implicit dead-letter — queryable for ops triage, excluded from the PENDING scan.
 */
public enum OutboxStatus {
    PENDING,
    DONE,
    FAILED
}
