package com.novatech.cybertech.entities;

import com.novatech.cybertech.entities.enums.OutboxOperationType;
import com.novatech.cybertech.entities.enums.OutboxStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * Durable record of an intended Keycloak side-effect (saga outbox).
 *
 * <p>Written before/with the risky external call so a process crash cannot lose the intention.
 * Reconciled by {@code KeycloakOutboxReconciliationTasklet}. Carries NO secret (never a password):
 * for CREATE the job reconciles by {@code email} lookup; for UPDATE it re-applies the persisted
 * {@code payload} idempotently; for DELETE it re-issues the idempotent delete by {@code keycloakId}.
 *
 * <p>See {@code docs/superpowers/specs/2026-06-04-keycloak-outbox-design.md}.
 */
@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true)
@Table(name = "keycloak_outbox", indexes = {
        @Index(name = "idx_outbox_status", columnList = "status"),
        @Index(name = "idx_outbox_email", columnList = "email")
})
public class KeycloakOutboxEntity extends BaseEntity<Long> {

    @Enumerated(EnumType.STRING)
    @Column(name = "operationType", nullable = false, updatable = false, length = 16)
    private OutboxOperationType operationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private OutboxStatus status;

    /** Set for UPDATE/DELETE at creation; for CREATE recorded at DONE (audit). */
    @Column(name = "keycloakId")
    private String keycloakId;

    /** CREATE reconciliation key (Keycloak lookup by email). Null for UPDATE/DELETE. */
    @Column(name = "email")
    private String email;

    /** UPDATE re-application payload: JSON-serialized {@code UserUpdateRequestDto}. Null for CREATE/DELETE. */
    @Column(name = "payload", length = 2000)
    private String payload;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "lastError", length = 1000)
    private String lastError;
}
