package com.novatech.cybertech.events.listener;

import com.novatech.cybertech.entities.KeycloakOutboxEntity;
import com.novatech.cybertech.entities.enums.OutboxOperationType;
import com.novatech.cybertech.entities.enums.OutboxStatus;
import com.novatech.cybertech.events.UserDeletedEvent;
import com.novatech.cybertech.repositories.KeycloakOutboxRepository;
import com.novatech.cybertech.services.core.KeycloakOutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mockito unit tests for {@link UserDeletionListener} — outbox edition.
 *
 * <p>The listener is now merely the LOW-LATENCY path of the DELETE saga: the durable intent
 * lives in the co-committed {@code keycloak_outbox} row; the listener resolves it by uuid and
 * delegates the idempotent work to {@link KeycloakOutboxService#reconcile}. Pinned contracts:
 * <ul>
 *   <li>resolves the row by the event's outboxUuid and reconciles it with the configured cap;</li>
 *   <li>a missing row (already drained / purged) is a silent no-op;</li>
 *   <li>any failure is logged and swallowed (post-commit thread) — the batch job retries.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class UserDeletionListenerTest {

    @Mock KeycloakOutboxRepository outboxRepository;
    @Mock KeycloakOutboxService keycloakOutboxService;

    @InjectMocks UserDeletionListener listener;

    @BeforeEach
    void wireMaxAttempts() {
        ReflectionTestUtils.setField(listener, "maxAttempts", 5);
    }

    private KeycloakOutboxEntity pendingDeleteRow() {
        return KeycloakOutboxEntity.builder()
                .operationType(OutboxOperationType.DELETE)
                .status(OutboxStatus.PENDING)
                .keycloakId("kc-deleted-123")
                .attempts(0)
                .build();
    }

    @Test
    @DisplayName("onUserDeleted resolves the co-committed row and reconciles it immediately")
    void onUserDeletedShouldReconcileTheOutboxRow() {
        UUID outboxUuid = UUID.randomUUID();
        KeycloakOutboxEntity row = pendingDeleteRow();
        when(outboxRepository.findByUuid(outboxUuid)).thenReturn(Optional.of(row));

        listener.onUserDeleted(new UserDeletedEvent(this, outboxUuid));

        verify(keycloakOutboxService).reconcile(row, 5);
    }

    @Test
    @DisplayName("missing outbox row (already drained) is a silent no-op")
    void onUserDeletedMissingRowIsNoop() {
        UUID outboxUuid = UUID.randomUUID();
        when(outboxRepository.findByUuid(outboxUuid)).thenReturn(Optional.empty());

        assertThatCode(() -> listener.onUserDeleted(new UserDeletedEvent(this, outboxUuid)))
                .doesNotThrowAnyException();

        verify(keycloakOutboxService, never()).reconcile(any(), anyInt());
    }

    @Test
    @DisplayName("failures are logged and swallowed (post-commit thread) — the batch job is the backstop")
    void onUserDeletedShouldLogAndSwallowWhenReconcileFails() {
        UUID outboxUuid = UUID.randomUUID();
        KeycloakOutboxEntity row = pendingDeleteRow();
        when(outboxRepository.findByUuid(outboxUuid)).thenReturn(Optional.of(row));
        doThrow(new RuntimeException("kc 500")).when(keycloakOutboxService).reconcile(row, 5);

        assertThatCode(() -> listener.onUserDeleted(new UserDeletedEvent(this, outboxUuid)))
                .doesNotThrowAnyException();

        verify(keycloakOutboxService).reconcile(row, 5);
    }
}
