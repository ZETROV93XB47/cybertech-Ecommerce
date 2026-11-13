package com.novatech.cybertech.events.listener;

import com.novatech.cybertech.events.UserDeletedEvent;
import com.novatech.cybertech.repositories.KeycloakOutboxRepository;
import com.novatech.cybertech.services.core.KeycloakOutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Immediate (AFTER_COMMIT) reconciliation of the DELETE outbox row so the Keycloak identity is
 * revoked promptly on the happy path.
 *
 * <p><b>Durability shift:</b> before the outbox, this listener WAS the only carrier of the
 * Keycloak-delete intent (an in-memory event — lost on crash). The intent now lives in the
 * {@code keycloak_outbox} row co-committed with the SQL delete; this listener is merely the
 * low-latency path. If it fails or the process dies, the row stays PENDING and the
 * reconciliation batch job drains it.
 *
 * <p><b>Exception contract:</b> unchanged — we run post-commit on the publisher's thread;
 * throwing would not undo the SQL delete. Failures are logged and swallowed; the job retries.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserDeletionListener {

    private final KeycloakOutboxRepository outboxRepository;
    private final KeycloakOutboxService keycloakOutboxService;

    @Value("${cybertech.keycloak.outbox.max-attempts:5}")
    private int maxAttempts;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserDeleted(final UserDeletedEvent event) {
        try {
            outboxRepository.findByUuid(event.getOutboxUuid())
                    .ifPresent(row -> keycloakOutboxService.reconcile(row, maxAttempts));
        } catch (Exception e) {
            log.error("Immediate reconcile of delete outbox {} failed — batch job will retry",
                    event.getOutboxUuid(), e);
        }
    }
}
