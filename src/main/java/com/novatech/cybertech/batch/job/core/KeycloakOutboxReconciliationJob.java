package com.novatech.cybertech.batch.job.core;

/**
 * Cron entry point for Keycloak-outbox crash recovery (user-saga CREATE/UPDATE/DELETE).
 *
 * <p>The synchronous request path (see {@code UserManagementServiceImp}) already performs every
 * Keycloak effect immediately — this job only exists to drain
 * {@code KeycloakOutboxEntity} rows a crash left {@code PENDING} beyond the staleness window,
 * delegating the actual reconciliation logic to
 * {@link com.novatech.cybertech.services.core.KeycloakOutboxService#reconcile}. See
 * {@code docs/superpowers/specs/2026-06-04-keycloak-outbox-design.md}.
 */
public interface KeycloakOutboxReconciliationJob {

    /** Launches the reconciliation batch job when {@code cybertech.keycloak.outbox.job.activated} is true. */
    void startJob();
}
