package com.novatech.cybertech.services.implementation.saga;

import com.novatech.cybertech.dto.request.user.UserUpdateRequestDto;
import com.novatech.cybertech.entities.KeycloakOutboxEntity;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.entities.enums.OutboxOperationType;
import com.novatech.cybertech.entities.enums.OutboxStatus;
import com.novatech.cybertech.fixtures.builders.UserEntityBuilder;
import com.novatech.cybertech.repositories.KeycloakOutboxRepository;
import com.novatech.cybertech.repositories.UserRepository;
import com.novatech.cybertech.services.implementation.KeycloakOutboxServiceImp;
import com.novatech.cybertech.services.implementation.KeycloakUserManagementService;
import com.novatech.cybertech.services.implementation.UserPersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Mockito unit tests for {@link KeycloakOutboxServiceImp} — the idempotent reconciliation core of
 * the Keycloak/DB user saga (CREATE + UPDATE + DELETE outbox).
 *
 * <p>Pinned invariants:
 * <ul>
 *   <li>reconcile NEVER creates a Keycloak user (no password at rest — design lock #2);</li>
 *   <li>CREATE: orphan → compensated; fully-registered → closed DONE without compensation;</li>
 *   <li>UPDATE: forward recovery re-applies the payload to both systems, with a supersede guard;</li>
 *   <li>DELETE: idempotent re-issue;</li>
 *   <li>transient failure bumps {@code attempts}; the cap flips terminal FAILED (implicit DLB).</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class KeycloakOutboxServiceImpTest {

    @Mock KeycloakOutboxRepository outboxRepository;
    @Mock UserRepository userRepository;
    @Mock KeycloakUserManagementService keycloakService;
    @Mock UserPersistenceService userPersistenceService;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private KeycloakOutboxServiceImp service;

    @BeforeEach
    void setUp() {
        service = new KeycloakOutboxServiceImp(
                outboxRepository, userRepository, keycloakService, userPersistenceService, objectMapper);
    }

    private KeycloakOutboxEntity createRow(String email) {
        return KeycloakOutboxEntity.builder()
                .operationType(OutboxOperationType.CREATE).status(OutboxStatus.PENDING)
                .email(email).attempts(0).build();
    }

    private KeycloakOutboxEntity deleteRow(String keycloakId) {
        return KeycloakOutboxEntity.builder()
                .operationType(OutboxOperationType.DELETE).status(OutboxStatus.PENDING)
                .keycloakId(keycloakId).attempts(0).build();
    }

    private KeycloakOutboxEntity updateRow(String keycloakId, UserUpdateRequestDto dto) {
        return KeycloakOutboxEntity.builder()
                .operationType(OutboxOperationType.UPDATE).status(OutboxStatus.PENDING)
                .keycloakId(keycloakId).payload(objectMapper.writeValueAsString(dto))
                .createdAt(LocalDateTime.now())
                .attempts(0).build();
    }

    // -----------------------------------------------------------------
    @Nested
    @DisplayName("record* — breadcrumb writes")
    class Record {

        @Test
        @DisplayName("recordCreatePending persists a PENDING CREATE row keyed by email")
        void recordCreatePending_persistsPendingRow() {
            when(outboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.recordCreatePending("a@b.com");

            ArgumentCaptor<KeycloakOutboxEntity> captor = ArgumentCaptor.forClass(KeycloakOutboxEntity.class);
            verify(outboxRepository).save(captor.capture());
            assertThat(captor.getValue().getOperationType()).isEqualTo(OutboxOperationType.CREATE);
            assertThat(captor.getValue().getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(captor.getValue().getEmail()).isEqualTo("a@b.com");
            assertThat(captor.getValue().getPayload()).isNull(); // never a password / never a payload for CREATE
        }

        @Test
        @DisplayName("recordUpdatePending serializes the patch DTO as JSON payload (no secret inside)")
        void recordUpdatePending_serializesPayload() {
            when(outboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            UserUpdateRequestDto dto = new UserUpdateRequestDto();
            dto.setUuid(UUID.randomUUID());
            dto.setFirstName("Alice");

            service.recordUpdatePending("kc-7", dto);

            ArgumentCaptor<KeycloakOutboxEntity> captor = ArgumentCaptor.forClass(KeycloakOutboxEntity.class);
            verify(outboxRepository).save(captor.capture());
            assertThat(captor.getValue().getOperationType()).isEqualTo(OutboxOperationType.UPDATE);
            assertThat(captor.getValue().getKeycloakId()).isEqualTo("kc-7");
            assertThat(captor.getValue().getPayload()).contains("Alice");
        }

        @Test
        @DisplayName("recordDeletePending persists a PENDING DELETE row keyed by keycloakId")
        void recordDeletePending_persistsPendingRow() {
            when(outboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.recordDeletePending("kc-9");

            ArgumentCaptor<KeycloakOutboxEntity> captor = ArgumentCaptor.forClass(KeycloakOutboxEntity.class);
            verify(outboxRepository).save(captor.capture());
            assertThat(captor.getValue().getOperationType()).isEqualTo(OutboxOperationType.DELETE);
            assertThat(captor.getValue().getKeycloakId()).isEqualTo("kc-9");
        }
    }

    // -----------------------------------------------------------------
    @Nested
    @DisplayName("reconcile CREATE — lookup + compensate, never re-create")
    class ReconcileCreate {

        @Test
        @DisplayName("orphan in Keycloak (no DB row) → compensated (deleted) + FAILED")
        void reconcileCreate_orphanInKeycloak_isCompensated() {
            final KeycloakOutboxEntity row = createRow("orphan@b.com");
            when(keycloakService.searchByEmail("orphan@b.com")).thenReturn(Optional.of("kc-9"));
            when(userRepository.findByKeycloakId("kc-9")).thenReturn(Optional.empty());

            service.reconcile(row, 5);

            verify(keycloakService).deleteUser("kc-9"); // orphan removed
            assertThat(row.getStatus()).isEqualTo(OutboxStatus.FAILED);
            verify(outboxRepository).save(row);
        }

        @Test
        @DisplayName("fully registered (Keycloak + DB both exist) → closed DONE, NOT compensated")
        void reconcileCreate_fullyRegistered_isClosedNotCompensated() {
            final KeycloakOutboxEntity row = createRow("ok@b.com");
            when(keycloakService.searchByEmail("ok@b.com")).thenReturn(Optional.of("kc-1"));
            when(userRepository.findByKeycloakId("kc-1")).thenReturn(Optional.of(new UserEntity()));

            service.reconcile(row, 5);

            verify(keycloakService, never()).deleteUser(any()); // must NOT delete a real user
            assertThat(row.getStatus()).isEqualTo(OutboxStatus.DONE);
            assertThat(row.getKeycloakId()).isEqualTo("kc-1"); // audit backfill
        }

        @Test
        @DisplayName("nothing created in Keycloak → abandoned as FAILED (job never re-creates: no password at rest)")
        void reconcileCreate_nothingCreated_isFailed() {
            final KeycloakOutboxEntity row = createRow("nobody@b.com");
            when(keycloakService.searchByEmail("nobody@b.com")).thenReturn(Optional.empty());

            service.reconcile(row, 5);

            verify(keycloakService, never()).deleteUser(any());
            assertThat(row.getStatus()).isEqualTo(OutboxStatus.FAILED);
        }
    }

    // -----------------------------------------------------------------
    @Nested
    @DisplayName("reconcile UPDATE — forward recovery with supersede guard")
    class ReconcileUpdate {

        @Test
        @DisplayName("stale crash leftover → payload re-applied to Keycloak AND DB, row DONE")
        void reconcileUpdate_reappliesToBothSystems() {
            UserUpdateRequestDto dto = new UserUpdateRequestDto();
            dto.setUuid(UUID.randomUUID());
            dto.setFirstName("Replayed");
            final KeycloakOutboxEntity row = updateRow("kc-up", dto);
            // DB row untouched since before the breadcrumb -> re-apply.
            final UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId("kc-up").build();
            when(userRepository.findByKeycloakId("kc-up")).thenReturn(Optional.of(user));

            service.reconcile(row, 5);

            ArgumentCaptor<UserUpdateRequestDto> kcDto = ArgumentCaptor.forClass(UserUpdateRequestDto.class);
            verify(keycloakService).updateUser(eq("kc-up"), kcDto.capture());
            assertThat(kcDto.getValue().getFirstName()).isEqualTo("Replayed");
            verify(userPersistenceService).updateUser(any(UserUpdateRequestDto.class), eq(user));
            assertThat(row.getStatus()).isEqualTo(OutboxStatus.DONE);
        }

        @Test
        @DisplayName("user no longer exists → nothing to converge (DELETE saga owns cleanup), row DONE")
        void reconcileUpdate_userGone_closesWithoutCalls() {
            UserUpdateRequestDto dto = new UserUpdateRequestDto();
            dto.setUuid(UUID.randomUUID());
            final KeycloakOutboxEntity row = updateRow("kc-gone", dto);
            when(userRepository.findByKeycloakId("kc-gone")).thenReturn(Optional.empty());

            service.reconcile(row, 5);

            verify(keycloakService, never()).updateUser(any(), any());
            verifyNoInteractions(userPersistenceService);
            assertThat(row.getStatus()).isEqualTo(OutboxStatus.DONE);
            assertThat(row.getLastError()).contains("user gone");
        }

        @Test
        @DisplayName("DB row written AFTER the breadcrumb → superseded, stale payload NOT re-applied")
        void reconcileUpdate_supersededByNewerWrite_skipsReapplication() {
            UserUpdateRequestDto dto = new UserUpdateRequestDto();
            dto.setUuid(UUID.randomUUID());
            dto.setFirstName("Stale");
            final KeycloakOutboxEntity row = updateRow("kc-new", dto);
            // The user row carries a LastModifiedDate AFTER the outbox row's creation -> newer write won.
            final UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId("kc-new")
                    .updatedAt(LocalDateTime.now().plusMinutes(10)).build();
            when(userRepository.findByKeycloakId("kc-new")).thenReturn(Optional.of(user));

            service.reconcile(row, 5);

            verify(keycloakService, never()).updateUser(any(), any());
            verifyNoInteractions(userPersistenceService);
            assertThat(row.getStatus()).isEqualTo(OutboxStatus.DONE);
            assertThat(row.getLastError()).contains("superseded");
        }
    }

    // -----------------------------------------------------------------
    @Nested
    @DisplayName("reconcile DELETE + retry/cap policy")
    class ReconcileDeleteAndRetries {

        @Test
        @DisplayName("DELETE → re-issues the idempotent delete and marks DONE")
        void reconcileDelete_reissuesIdempotentDelete_andMarksDone() {
            final KeycloakOutboxEntity row = deleteRow("kc-del");

            service.reconcile(row, 5);

            verify(keycloakService).deleteUser("kc-del");
            assertThat(row.getStatus()).isEqualTo(OutboxStatus.DONE);
        }

        @Test
        @DisplayName("transient failure → attempts bumped, row STAYS PENDING for the next tick")
        void reconcile_transientFailure_bumpsAttempts_staysPending() {
            final KeycloakOutboxEntity row = deleteRow("kc-x");
            doThrow(new RuntimeException("kc down")).when(keycloakService).deleteUser("kc-x");

            service.reconcile(row, 5);

            assertThat(row.getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(row.getAttempts()).isEqualTo(1);
            assertThat(row.getLastError()).contains("kc down");
            verify(outboxRepository).save(row);
        }

        @Test
        @DisplayName("attempts cap reached → row flips terminal FAILED (implicit dead-letter)")
        void reconcile_overAttemptsCap_flipsFailed() {
            final KeycloakOutboxEntity row = deleteRow("kc-x");
            row.setAttempts(4);
            doThrow(new RuntimeException("still down")).when(keycloakService).deleteUser("kc-x");

            service.reconcile(row, 5); // 4 -> 5 == cap

            assertThat(row.getStatus()).isEqualTo(OutboxStatus.FAILED);
            assertThat(row.getAttempts()).isEqualTo(5);
        }
    }

    // -----------------------------------------------------------------
    @Nested
    @DisplayName("markDone / markFailed")
    class Marks {

        @Test
        @DisplayName("markDone flips DONE and backfills keycloakId when supplied")
        void markDone_flipsAndBackfills() {
            final UUID uuid = UUID.randomUUID();
            final KeycloakOutboxEntity row = createRow("a@b.com");
            when(outboxRepository.findByUuid(uuid)).thenReturn(Optional.of(row));

            service.markDone(uuid, "kc-42");

            assertThat(row.getStatus()).isEqualTo(OutboxStatus.DONE);
            assertThat(row.getKeycloakId()).isEqualTo("kc-42");
            verify(outboxRepository).save(row);
        }

        @Test
        @DisplayName("markFailed flips FAILED and truncates the reason into lastError")
        void markFailed_flipsWithReason() {
            final UUID uuid = UUID.randomUUID();
            final KeycloakOutboxEntity row = createRow("a@b.com");
            when(outboxRepository.findByUuid(uuid)).thenReturn(Optional.of(row));

            service.markFailed(uuid, "x".repeat(1500));

            assertThat(row.getStatus()).isEqualTo(OutboxStatus.FAILED);
            assertThat(row.getLastError()).hasSize(1000);
        }

        @Test
        @DisplayName("unknown uuid is a silent no-op (row may have been purged)")
        void marks_unknownUuid_noop() {
            final UUID uuid = UUID.randomUUID();
            when(outboxRepository.findByUuid(uuid)).thenReturn(Optional.empty());

            service.markDone(uuid, null);
            service.markFailed(uuid, "whatever");

            verify(outboxRepository, never()).save(any());
        }
    }
}
