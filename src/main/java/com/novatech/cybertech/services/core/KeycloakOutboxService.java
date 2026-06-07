package com.novatech.cybertech.services.core;

import com.novatech.cybertech.dto.request.user.UserUpdateRequestDto;
import com.novatech.cybertech.entities.KeycloakOutboxEntity;

import java.util.UUID;

/**
 * Writes and reconciles {@link KeycloakOutboxEntity} rows — the durable half of the Keycloak/DB
 * user saga (CREATE + UPDATE + DELETE). See
 * {@code docs/superpowers/specs/2026-06-04-keycloak-outbox-design.md}.
 */
public interface KeycloakOutboxService {

    /** CREATE breadcrumb: durable PENDING intent committed in its OWN transaction. Returns row uuid. */
    UUID recordCreatePending(String email);

    /**
     * UPDATE breadcrumb: durable PENDING intent (own transaction) carrying the JSON-serialized
     * patch payload so the job can re-apply it idempotently after a crash. Returns row uuid.
     */
    UUID recordUpdatePending(String keycloakId, UserUpdateRequestDto payload);

    /** DELETE breadcrumb: PENDING row written to JOIN the caller's transaction (co-commit with the DB delete). Returns row uuid. */
    UUID recordDeletePending(String keycloakId);

    /** Mark a row DONE (own transaction). keycloakId optional (audit for CREATE). */
    void markDone(UUID outboxUuid, String keycloakId);

    /** Mark a row FAILED with a reason (own transaction). */
    void markFailed(UUID outboxUuid, String reason);

    /** Idempotent reconciliation of one row, used by the batch job. Bumps attempts / flips terminal state. */
    void reconcile(KeycloakOutboxEntity row, int maxAttempts);
}
