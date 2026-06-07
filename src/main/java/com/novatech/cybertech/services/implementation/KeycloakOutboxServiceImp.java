package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.dto.request.user.UserUpdateRequestDto;
import com.novatech.cybertech.entities.KeycloakOutboxEntity;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.entities.enums.OutboxOperationType;
import com.novatech.cybertech.entities.enums.OutboxStatus;
import com.novatech.cybertech.repositories.KeycloakOutboxRepository;
import com.novatech.cybertech.repositories.UserRepository;
import com.novatech.cybertech.services.core.KeycloakOutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

/**
 * Outbox bookkeeping + idempotent reconciliation for the Keycloak/DB user saga.
 *
 * <p>The reconciliation NEVER creates a Keycloak user (the raw password is never at rest):
 * <ul>
 *   <li>CREATE — email lookup; orphan (Keycloak user without DB row) is compensated (deleted),
 *       a fully-registered pair is simply closed DONE.</li>
 *   <li>UPDATE — the persisted payload is re-applied idempotently to BOTH systems (forward
 *       recovery), unless a newer write already landed on the DB row (supersede guard).</li>
 *   <li>DELETE — the idempotent (404-tolerant) Keycloak delete is re-issued.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KeycloakOutboxServiceImp implements KeycloakOutboxService {

    private static final int MAX_ERROR_LEN = 1000;

    private final KeycloakOutboxRepository outboxRepository;
    private final UserRepository userRepository;
    private final KeycloakUserManagementService keycloakUserManagementService;
    private final UserPersistenceService userPersistenceService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID recordCreatePending(final String email) {
        final KeycloakOutboxEntity row = KeycloakOutboxEntity.builder()
                .operationType(OutboxOperationType.CREATE).status(OutboxStatus.PENDING)
                .email(email).attempts(0).build();
        return outboxRepository.save(row).getUuid();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID recordUpdatePending(final String keycloakId, final UserUpdateRequestDto payload) {
        final KeycloakOutboxEntity row = KeycloakOutboxEntity.builder()
                .operationType(OutboxOperationType.UPDATE).status(OutboxStatus.PENDING)
                .keycloakId(keycloakId).payload(objectMapper.writeValueAsString(payload))
                .attempts(0).build();
        return outboxRepository.save(row).getUuid();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED) // JOIN the caller's delete TX (co-commit)
    public UUID recordDeletePending(final String keycloakId) {
        final KeycloakOutboxEntity row = KeycloakOutboxEntity.builder()
                .operationType(OutboxOperationType.DELETE).status(OutboxStatus.PENDING)
                .keycloakId(keycloakId).attempts(0).build();
        return outboxRepository.save(row).getUuid();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markDone(final UUID outboxUuid, final String keycloakId) {
        outboxRepository.findByUuid(outboxUuid).ifPresent(row -> {
            row.setStatus(OutboxStatus.DONE);
            if (keycloakId != null) {
                row.setKeycloakId(keycloakId);
            }
            outboxRepository.save(row);
        });
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(final UUID outboxUuid, final String reason) {
        outboxRepository.findByUuid(outboxUuid).ifPresent(row -> {
            row.setStatus(OutboxStatus.FAILED);
            row.setLastError(truncate(reason));
            outboxRepository.save(row);
        });
    }

    /**
     * Idempotent reconciliation. The job calls this for a stale PENDING row. On a transient
     * failure it bumps {@code attempts}; at the cap it flips the row terminal FAILED (the
     * implicit dead-letter — no separate DLB table by design).
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reconcile(final KeycloakOutboxEntity row, final int maxAttempts) {
        try {
            switch (row.getOperationType()) {
                case CREATE -> reconcileCreate(row);
                case UPDATE -> reconcileUpdate(row);
                case DELETE -> reconcileDelete(row);
            }
        } catch (RuntimeException e) {
            row.setAttempts(row.getAttempts() + 1);
            row.setLastError(truncate(e.getMessage()));
            if (row.getAttempts() >= maxAttempts) {
                row.setStatus(OutboxStatus.FAILED);
                log.error("Outbox row {} ({}) gave up after {} attempts — manual reconciliation required",
                        row.getUuid(), row.getOperationType(), row.getAttempts(), e);
            } else {
                log.warn("Outbox row {} ({}) transient failure, attempt {} — will retry",
                        row.getUuid(), row.getOperationType(), row.getAttempts(), e);
            }
            outboxRepository.save(row);
        }
    }

    private void reconcileCreate(final KeycloakOutboxEntity row) {
        final var kcId = keycloakUserManagementService.searchByEmail(row.getEmail());
        if (kcId.isEmpty()) {
            // Nothing was ever created in Keycloak -> the registration never got off the ground. Abandon.
            row.setStatus(OutboxStatus.FAILED);
            row.setLastError(truncate("create never reached Keycloak — abandoned"));
            outboxRepository.save(row);
            return;
        }
        final boolean dbUserExists = userRepository.findByKeycloakId(kcId.get()).isPresent();
        if (dbUserExists) {
            // Fully registered; the row just didn't get marked DONE. Close it — DO NOT compensate.
            row.setStatus(OutboxStatus.DONE);
            row.setKeycloakId(kcId.get());
        } else {
            // Keycloak user with no DB row = crash orphan. Compensate (backward recovery).
            keycloakUserManagementService.deleteUser(kcId.get());
            row.setStatus(OutboxStatus.FAILED);
            row.setLastError(truncate("orphan Keycloak user compensated"));
        }
        outboxRepository.save(row);
    }

    /**
     * Forward recovery for UPDATE: re-apply the persisted patch to Keycloak AND the DB so both
     * converge on the intended state. Two guards keep stale payloads from doing harm:
     * <ul>
     *   <li>the user no longer exists → nothing to converge (the DELETE saga owns Keycloak
     *       cleanup) — close DONE;</li>
     *   <li>the DB row was written again AFTER this breadcrumb was created → a newer update
     *       superseded this one; re-applying would resurrect old data — close DONE without
     *       touching either system.</li>
     * </ul>
     */
    private void reconcileUpdate(final KeycloakOutboxEntity row) {
        final UserEntity user = userRepository.findByKeycloakId(row.getKeycloakId()).orElse(null);
        if (user == null) {
            row.setStatus(OutboxStatus.DONE);
            row.setLastError(truncate("user gone — update superseded by deletion"));
            outboxRepository.save(row);
            return;
        }
        if (user.getUpdatedAt() != null && row.getCreatedAt() != null
                && user.getUpdatedAt().isAfter(row.getCreatedAt())) {
            row.setStatus(OutboxStatus.DONE);
            row.setLastError(truncate("superseded by a newer write — not re-applied"));
            outboxRepository.save(row);
            return;
        }
        final UserUpdateRequestDto dto = objectMapper.readValue(row.getPayload(), UserUpdateRequestDto.class);
        keycloakUserManagementService.updateUser(row.getKeycloakId(), dto); // idempotent re-application
        userPersistenceService.updateUser(dto, user);                       // idempotent DB patch (REQUIRES_NEW)
        row.setStatus(OutboxStatus.DONE);
        outboxRepository.save(row);
    }

    private void reconcileDelete(final KeycloakOutboxEntity row) {
        keycloakUserManagementService.deleteUser(row.getKeycloakId()); // idempotent (404-tolerant)
        row.setStatus(OutboxStatus.DONE);
        outboxRepository.save(row);
    }

    private static String truncate(final String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= MAX_ERROR_LEN ? s : s.substring(0, MAX_ERROR_LEN);
    }
}
