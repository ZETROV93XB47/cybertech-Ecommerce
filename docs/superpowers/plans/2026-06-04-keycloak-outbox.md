# Keycloak Outbox (CREATE + UPDATE + DELETE) Implementation Plan

> **Révision 2026-06-08 — IMPLÉMENTÉ.** Scope élargi à l'UPDATE (breadcrumb avec payload JSON +
> ré-application idempotente + supersede guard) sur décision utilisateur. Les tâches 1-6 sont
> livrées sur `dev/develop` (commits `6b24724`, `504766f`, `35d797d` + IT). Différences notables
> vs le plan original : pas de `@DataJpaTest` (aucun précédent projet — la derived query est
> couverte par l'IT), `create()` marque aussi FAILED sur échec Keycloak propre, l'entité porte une
> colonne `payload` (UPDATE), et `deleteUser` surfaçait silencieusement les erreurs — corrigé.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Keycloak/DB user saga crash-safe for `create` (durable compensation breadcrumb) and `delete` (durable co-committed outbox replacing the in-memory AFTER_COMMIT event), reconciled by a `@Scheduled` Spring Batch job.

**Architecture:** A single `keycloak_outbox` table in the existing MySQL records the durable intent of a Keycloak side-effect. The synchronous request path performs the effect immediately; a periodic batch tasklet reconciles rows a crash left `PENDING`. The job NEVER creates a Keycloak user (no raw password at rest) — for CREATE it reconciles by lookup-and-compensate (delete the orphan); for DELETE it re-issues the idempotent delete.

**Tech Stack:** Spring Boot 4.0.4, Spring Batch, JPA/MySQL, Keycloak admin client, JUnit 5 + Mockito + AssertJ, Testcontainers.

**Build/test command (Windows, JDK 26):**
```
JAVA_HOME="C:\Program Files\Java\jdk-26" ./mvnw.cmd test -Dtest=<Class> -DfailIfNoTests=false
```
Reference spec: `docs/superpowers/specs/2026-06-04-keycloak-outbox-design.md`.

---

## File Structure

**Create:**
- `src/main/java/com/novatech/cybertech/entities/enums/OutboxOperationType.java` — `{ CREATE, DELETE }`
- `src/main/java/com/novatech/cybertech/entities/enums/OutboxStatus.java` — `{ PENDING, DONE, FAILED }`
- `src/main/java/com/novatech/cybertech/entities/KeycloakOutboxEntity.java` — the outbox row
- `src/main/java/com/novatech/cybertech/repositories/KeycloakOutboxRepository.java`
- `src/main/java/com/novatech/cybertech/services/core/KeycloakOutboxService.java` — interface
- `src/main/java/com/novatech/cybertech/services/implementation/KeycloakOutboxServiceImp.java` — impl (record + reconcile)
- `src/main/java/com/novatech/cybertech/batch/task/KeycloakOutboxReconciliationTasklet.java`
- `src/main/java/com/novatech/cybertech/batch/job/KeycloakOutboxReconciliationJob.java` — `@Scheduled` launcher
- Tests mirroring each.

**Modify:**
- `src/main/java/com/novatech/cybertech/services/implementation/KeycloakUserManagementService.java` — add `searchByEmail`; make `deleteUser` 404-tolerant.
- `src/main/java/com/novatech/cybertech/services/implementation/UserManagementServiceImp.java` — CREATE breadcrumb; DELETE co-commit + repurposed event.
- `src/main/java/com/novatech/cybertech/events/UserDeletedEvent.java` — carry the outbox row UUID instead of keycloakId.
- `src/main/java/com/novatech/cybertech/events/listener/UserDeletionListener.java` — trigger immediate reconcile of the co-committed row.
- `src/main/java/com/novatech/cybertech/config/BatchConfig.java` — Job + Step beans.
- `src/main/java/com/novatech/cybertech/constants/CyberTechAppConstants.java` — job/tasklet name constants.
- `src/main/resources/application.properties` — cron / activated / staleness / max-attempts / batch-size.
- `src/main/resources/sql/databaseSchemaInitFile.sql` — `keycloak_outbox` table.

---

## Task 1: Outbox data layer (enums + entity + repository + schema)

**Files:**
- Create: `entities/enums/OutboxOperationType.java`, `entities/enums/OutboxStatus.java`, `entities/KeycloakOutboxEntity.java`, `repositories/KeycloakOutboxRepository.java`
- Modify: `src/main/resources/sql/databaseSchemaInitFile.sql`
- Test: `src/test/java/com/novatech/cybertech/repositories/KeycloakOutboxRepositoryTest.java` (`@DataJpaTest`)

- [ ] **Step 1: Create the two enums**

`entities/enums/OutboxOperationType.java`:
```java
package com.novatech.cybertech.entities.enums;

public enum OutboxOperationType {
    CREATE,
    DELETE
}
```
`entities/enums/OutboxStatus.java`:
```java
package com.novatech.cybertech.entities.enums;

public enum OutboxStatus {
    PENDING,
    DONE,
    FAILED
}
```

- [ ] **Step 2: Create the entity**

`entities/KeycloakOutboxEntity.java`:
```java
package com.novatech.cybertech.entities;

import com.novatech.cybertech.entities.enums.OutboxOperationType;
import com.novatech.cybertech.entities.enums.OutboxStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * Durable record of an intended Keycloak side-effect (BUG-SAGA outbox).
 *
 * <p>Written before/with the risky external call so a process crash cannot lose the intention.
 * Reconciled by {@code KeycloakOutboxReconciliationTasklet}. Carries NO secret (never a password):
 * for CREATE the job reconciles by {@code email} lookup; for DELETE it re-issues the idempotent
 * delete by {@code keycloakId}.
 */
@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "keycloak_outbox")
@ToString(callSuper = true)
public class KeycloakOutboxEntity extends BaseEntity<Long> {

    @Enumerated(EnumType.STRING)
    @Column(name = "operationType", nullable = false, updatable = false)
    private OutboxOperationType operationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OutboxStatus status;

    /** Set for DELETE at creation; for CREATE recorded at DONE (audit). */
    @Column(name = "keycloakId")
    private String keycloakId;

    /** CREATE reconciliation key (Keycloak lookup by email). Null for DELETE. */
    @Column(name = "email")
    private String email;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "lastError", length = 1000)
    private String lastError;
}
```

- [ ] **Step 3: Write the failing repository test**

`src/test/java/com/novatech/cybertech/repositories/KeycloakOutboxRepositoryTest.java`:
```java
package com.novatech.cybertech.repositories;

import com.novatech.cybertech.entities.KeycloakOutboxEntity;
import com.novatech.cybertech.entities.enums.OutboxOperationType;
import com.novatech.cybertech.entities.enums.OutboxStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class KeycloakOutboxRepositoryTest {

    @Autowired
    KeycloakOutboxRepository repository;

    private KeycloakOutboxEntity pending(OutboxOperationType type) {
        return KeycloakOutboxEntity.builder()
                .operationType(type).status(OutboxStatus.PENDING).attempts(0).build();
    }

    @Test
    void findsOnlyStalePendingRows() {
        repository.save(pending(OutboxOperationType.CREATE));
        KeycloakOutboxEntity done = pending(OutboxOperationType.DELETE);
        done.setStatus(OutboxStatus.DONE);
        repository.save(done);

        final List<KeycloakOutboxEntity> stale = repository.findByStatusAndUpdatedAtBefore(
                OutboxStatus.PENDING, LocalDateTime.now().plusMinutes(1), PageRequest.ofSize(50));

        assertThat(stale).hasSize(1);
        assertThat(stale.get(0).getOperationType()).isEqualTo(OutboxOperationType.CREATE);
    }
}
```

- [ ] **Step 4: Run it — expect FAIL (repository does not exist / compile error)**

Run: `JAVA_HOME="C:\Program Files\Java\jdk-26" ./mvnw.cmd test -Dtest=KeycloakOutboxRepositoryTest -DfailIfNoTests=false`
Expected: compile failure (`KeycloakOutboxRepository` not found).

- [ ] **Step 5: Create the repository**

`repositories/KeycloakOutboxRepository.java`:
```java
package com.novatech.cybertech.repositories;

import com.novatech.cybertech.entities.KeycloakOutboxEntity;
import com.novatech.cybertech.entities.enums.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface KeycloakOutboxRepository extends CrudBaseRepository<KeycloakOutboxEntity, Long> {

    /** Rows still PENDING and untouched since {@code threshold} — i.e. left dangling by a crash. */
    List<KeycloakOutboxEntity> findByStatusAndUpdatedAtBefore(OutboxStatus status,
                                                              LocalDateTime threshold,
                                                              Pageable pageable);
}
```

- [ ] **Step 6: Run it — expect PASS**

Run: same as Step 4. Expected: PASS.

- [ ] **Step 7: Add the production schema table**

In `src/main/resources/sql/databaseSchemaInitFile.sql`, append (match the casing/engine of the existing tables in that file):
```sql
CREATE TABLE IF NOT EXISTS keycloak_outbox (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    uuid          BINARY(16)   NOT NULL UNIQUE,
    operationType VARCHAR(16)  NOT NULL,
    status        VARCHAR(16)  NOT NULL,
    keycloakId    VARCHAR(255) NULL,
    email         VARCHAR(255) NULL,
    attempts      INT          NOT NULL DEFAULT 0,
    lastError     VARCHAR(1000) NULL,
    createdAt     DATETIME(6)  NOT NULL,
    updatedAt     DATETIME(6)  NULL,
    version       BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_outbox_status (status),
    INDEX idx_outbox_email (email)
);
```
> Note: confirm the actual UUID column type used by other tables in this file (e.g. `BINARY(16)` vs `CHAR(36)`) and match it. Testcontainers ITs use Hibernate `ddl-auto` from JPA metadata, so this SQL only governs the production init path.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/novatech/cybertech/entities/enums/OutboxOperationType.java \
        src/main/java/com/novatech/cybertech/entities/enums/OutboxStatus.java \
        src/main/java/com/novatech/cybertech/entities/KeycloakOutboxEntity.java \
        src/main/java/com/novatech/cybertech/repositories/KeycloakOutboxRepository.java \
        src/test/java/com/novatech/cybertech/repositories/KeycloakOutboxRepositoryTest.java \
        src/main/resources/sql/databaseSchemaInitFile.sql
git commit -m "feat(saga): keycloak_outbox entity + repository + schema"
```

---

## Task 2: Keycloak lookup + idempotent delete

**Files:**
- Modify: `services/implementation/KeycloakUserManagementService.java`
- Test: `src/test/java/com/novatech/cybertech/services/implementation/KeycloakUserManagementServiceTest.java` (create if absent)

- [ ] **Step 1: Write the failing test for `searchByEmail`**

`KeycloakUserManagementServiceTest.java` (add to existing or create):
```java
package com.novatech.cybertech.services.implementation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KeycloakUserManagementServiceTest {

    @Mock Keycloak keycloakClient;
    @Mock RealmResource realmResource;
    @Mock UsersResource usersResource;

    @InjectMocks KeycloakUserManagementService service;

    private void wireRealm() {
        ReflectionTestUtils.setField(service, "realm", "cybertech");
        lenient().when(keycloakClient.realm("cybertech")).thenReturn(realmResource);
        lenient().when(realmResource.users()).thenReturn(usersResource);
    }

    @Test
    void searchByEmail_returnsFirstMatchingId() {
        wireRealm();
        final UserRepresentation rep = new UserRepresentation();
        rep.setId("kc-123");
        when(usersResource.searchByEmail(eq("a@b.com"), eq(true))).thenReturn(List.of(rep));

        final Optional<String> id = service.searchByEmail("a@b.com");

        assertThat(id).contains("kc-123");
    }

    @Test
    void searchByEmail_emptyWhenNoMatch() {
        wireRealm();
        when(usersResource.searchByEmail(eq("none@b.com"), eq(true))).thenReturn(List.of());

        assertThat(service.searchByEmail("none@b.com")).isEmpty();
    }
}
```

- [ ] **Step 2: Run it — expect FAIL (`searchByEmail` not defined)**

Run: `JAVA_HOME="C:\Program Files\Java\jdk-26" ./mvnw.cmd test -Dtest=KeycloakUserManagementServiceTest -DfailIfNoTests=false`
Expected: compile failure.

- [ ] **Step 3: Implement `searchByEmail` + make `deleteUser` 404-tolerant**

In `KeycloakUserManagementService.java`, add imports `jakarta.ws.rs.NotFoundException`, `java.util.Optional`, and methods:
```java
    /**
     * Reconciliation lookup: resolve a Keycloak user id by exact email, or empty if none.
     * Used by the outbox job to detect a crash-orphan (Keycloak user with no DB row).
     */
    public Optional<String> searchByEmail(final String email) {
        return keycloakClient.realm(realm).users().searchByEmail(email, true).stream()
                .findFirst()
                .map(UserRepresentation::getId);
    }
```
Replace `deleteUser` with a 404-tolerant version (so re-issuing a delete on an already-deleted user is a no-op success — required for idempotent reconciliation):
```java
    public void deleteUser(final String keycloakUserId) {
        try (var ignored = keycloakClient.realm(realm).users().delete(keycloakUserId)) {
            // Response auto-closed. A 404 means the user is already gone — treat as success below.
        } catch (jakarta.ws.rs.NotFoundException nfe) {
            log.info("Keycloak user {} already absent on delete — treating as done (idempotent)", keycloakUserId);
        }
    }
```
> Note: `users().delete(id)` returns a JAX-RS `Response`; closing it avoids a leak. If the installed client signature returns `void`, drop the try-with-resources and keep only the `catch (NotFoundException)`. Verify against the resolved `keycloak-admin-client` version before finalizing.

- [ ] **Step 4: Run it — expect PASS**

Run: same as Step 2. Expected: PASS (both tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/novatech/cybertech/services/implementation/KeycloakUserManagementService.java \
        src/test/java/com/novatech/cybertech/services/implementation/KeycloakUserManagementServiceTest.java
git commit -m "feat(saga): Keycloak searchByEmail + idempotent (404-tolerant) deleteUser"
```

---

## Task 3: KeycloakOutboxService (record + reconcile)

**Files:**
- Create: `services/core/KeycloakOutboxService.java`, `services/implementation/KeycloakOutboxServiceImp.java`
- Test: `src/test/java/com/novatech/cybertech/services/implementation/saga/KeycloakOutboxServiceImpTest.java`

- [ ] **Step 1: Create the interface**

`services/core/KeycloakOutboxService.java`:
```java
package com.novatech.cybertech.services.core;

import com.novatech.cybertech.entities.KeycloakOutboxEntity;

import java.util.UUID;

/**
 * Writes and reconciles {@link KeycloakOutboxEntity} rows. See
 * docs/superpowers/specs/2026-06-04-keycloak-outbox-design.md.
 */
public interface KeycloakOutboxService {

    /** CREATE breadcrumb: durable PENDING intent committed in its OWN transaction. Returns row uuid. */
    UUID recordCreatePending(String email);

    /** DELETE breadcrumb: PENDING row written to JOIN the caller's transaction (co-commit with the DB delete). Returns row uuid. */
    UUID recordDeletePending(String keycloakId);

    /** Mark a row DONE (own transaction). keycloakId optional (audit for CREATE). */
    void markDone(UUID outboxUuid, String keycloakId);

    /** Mark a row FAILED with a reason (own transaction). */
    void markFailed(UUID outboxUuid, String reason);

    /** Idempotent reconciliation of one row, used by the batch job. Bumps attempts / flips terminal state. */
    void reconcile(KeycloakOutboxEntity row, int maxAttempts);
}
```

- [ ] **Step 2: Write the failing reconcile tests**

`src/test/java/com/novatech/cybertech/services/implementation/saga/KeycloakOutboxServiceImpTest.java`:
```java
package com.novatech.cybertech.services.implementation.saga;

import com.novatech.cybertech.entities.KeycloakOutboxEntity;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.entities.enums.OutboxOperationType;
import com.novatech.cybertech.entities.enums.OutboxStatus;
import com.novatech.cybertech.repositories.KeycloakOutboxRepository;
import com.novatech.cybertech.repositories.UserRepository;
import com.novatech.cybertech.services.implementation.KeycloakOutboxServiceImp;
import com.novatech.cybertech.services.implementation.KeycloakUserManagementService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KeycloakOutboxServiceImpTest {

    @Mock KeycloakOutboxRepository outboxRepository;
    @Mock UserRepository userRepository;
    @Mock KeycloakUserManagementService keycloakService;

    @InjectMocks KeycloakOutboxServiceImp service;

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

    @Test
    void reconcileCreate_orphanInKeycloak_isCompensated() {
        final KeycloakOutboxEntity row = createRow("orphan@b.com");
        when(keycloakService.searchByEmail("orphan@b.com")).thenReturn(Optional.of("kc-9"));
        when(userRepository.findByKeycloakId("kc-9")).thenReturn(Optional.empty());

        service.reconcile(row, 5);

        verify(keycloakService).deleteUser("kc-9");                 // orphan removed
        assertThat(row.getStatus()).isEqualTo(OutboxStatus.FAILED);
        verify(outboxRepository).save(row);
    }

    @Test
    void reconcileCreate_fullyRegistered_isClosedNotCompensated() {
        final KeycloakOutboxEntity row = createRow("ok@b.com");
        when(keycloakService.searchByEmail("ok@b.com")).thenReturn(Optional.of("kc-1"));
        when(userRepository.findByKeycloakId("kc-1")).thenReturn(Optional.of(new UserEntity()));

        service.reconcile(row, 5);

        verify(keycloakService, never()).deleteUser(any());        // must NOT delete a real user
        assertThat(row.getStatus()).isEqualTo(OutboxStatus.DONE);
    }

    @Test
    void reconcileCreate_nothingCreated_isFailed() {
        final KeycloakOutboxEntity row = createRow("nobody@b.com");
        when(keycloakService.searchByEmail("nobody@b.com")).thenReturn(Optional.empty());

        service.reconcile(row, 5);

        verify(keycloakService, never()).deleteUser(any());
        assertThat(row.getStatus()).isEqualTo(OutboxStatus.FAILED);
    }

    @Test
    void reconcileDelete_reissuesIdempotentDelete_andMarksDone() {
        final KeycloakOutboxEntity row = deleteRow("kc-del");

        service.reconcile(row, 5);

        verify(keycloakService).deleteUser("kc-del");
        assertThat(row.getStatus()).isEqualTo(OutboxStatus.DONE);
    }

    @Test
    void reconcile_transientFailure_bumpsAttempts_staysPending() {
        final KeycloakOutboxEntity row = deleteRow("kc-x");
        org.mockito.Mockito.doThrow(new RuntimeException("kc down")).when(keycloakService).deleteUser("kc-x");

        service.reconcile(row, 5);

        assertThat(row.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(row.getAttempts()).isEqualTo(1);
        assertThat(row.getLastError()).contains("kc down");
    }

    @Test
    void reconcile_overAttemptsCap_flipsFailed() {
        final KeycloakOutboxEntity row = deleteRow("kc-x");
        row.setAttempts(4);
        org.mockito.Mockito.doThrow(new RuntimeException("still down")).when(keycloakService).deleteUser("kc-x");

        service.reconcile(row, 5); // 4 -> 5 == cap

        assertThat(row.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(row.getAttempts()).isEqualTo(5);
    }
}
```

- [ ] **Step 3: Run — expect FAIL (impl missing)**

Run: `JAVA_HOME="C:\Program Files\Java\jdk-26" ./mvnw.cmd test -Dtest=KeycloakOutboxServiceImpTest -DfailIfNoTests=false`
Expected: compile failure (`KeycloakOutboxServiceImp` missing).

- [ ] **Step 4: Implement the service**

`services/implementation/KeycloakOutboxServiceImp.java`:
```java
package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.entities.KeycloakOutboxEntity;
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

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class KeycloakOutboxServiceImp implements KeycloakOutboxService {

    private static final int MAX_ERROR_LEN = 1000;

    private final KeycloakOutboxRepository outboxRepository;
    private final UserRepository userRepository;
    private final KeycloakUserManagementService keycloakUserManagementService;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID recordCreatePending(final String email) {
        final KeycloakOutboxEntity row = KeycloakOutboxEntity.builder()
                .operationType(OutboxOperationType.CREATE).status(OutboxStatus.PENDING)
                .email(email).attempts(0).build();
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
            if (keycloakId != null) row.setKeycloakId(keycloakId);
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
     * Idempotent reconciliation. The job calls this for a stale PENDING row. NEVER creates a Keycloak
     * user (no password at rest): CREATE reconciles by email lookup + compensation; DELETE re-issues
     * the idempotent delete. On a transient failure it bumps {@code attempts}; at the cap it flips FAILED.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reconcile(final KeycloakOutboxEntity row, final int maxAttempts) {
        try {
            switch (row.getOperationType()) {
                case CREATE -> reconcileCreate(row);
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

    private void reconcileDelete(final KeycloakOutboxEntity row) {
        keycloakUserManagementService.deleteUser(row.getKeycloakId()); // idempotent (404-tolerant)
        row.setStatus(OutboxStatus.DONE);
        outboxRepository.save(row);
    }

    private static String truncate(final String s) {
        if (s == null) return null;
        return s.length() <= MAX_ERROR_LEN ? s : s.substring(0, MAX_ERROR_LEN);
    }
}
```

- [ ] **Step 5: Run — expect PASS (6 tests)**

Run: same as Step 3. Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/novatech/cybertech/services/core/KeycloakOutboxService.java \
        src/main/java/com/novatech/cybertech/services/implementation/KeycloakOutboxServiceImp.java \
        src/test/java/com/novatech/cybertech/services/implementation/saga/KeycloakOutboxServiceImpTest.java
git commit -m "feat(saga): KeycloakOutboxService record + idempotent reconcile"
```

---

## Task 4: Wire the CREATE saga (breadcrumb + compensation)

**Files:**
- Modify: `services/implementation/UserManagementServiceImp.java` (`create` only)
- Test: `src/test/java/com/novatech/cybertech/services/implementation/...UserManagementServiceImpTest.java` (existing — add CREATE outbox tests)

> `UserPersistenceService.saveNewUser` is intentionally left UNCHANGED (2-arg). `markDone` is called
> from `create()` AFTER `saveNewUser` returns (i.e. after the DB commit), so a crash between the DB
> commit and `markDone` simply leaves the row PENDING — and the job's `reconcileCreate` closes it as
> DONE (Keycloak user + DB user both exist) without compensating. No co-commit gymnastics, no ripple
> to other `saveNewUser` call sites.

- [ ] **Step 1: Write the failing test (create records breadcrumb, persists, then marks DONE)**

Add to the existing `UserManagementServiceImpTest` (add field `@Mock KeycloakOutboxService keycloakOutboxService` to the mocks injected into `@InjectMocks UserManagementServiceImp`):
```java
    @Test
    void create_breadcrumb_thenKeycloak_thenPersist_thenDone() {
        final UUID outboxUuid = UUID.randomUUID();
        when(keycloakOutboxService.recordCreatePending(req.getEmail())).thenReturn(outboxUuid);
        when(keycloakUserManagementService.createUser(any(), any(), any(), any(), any())).thenReturn("kc-1");
        final UserResponseDto mapped = new UserResponseDto();
        when(userPersistenceService.saveNewUser(req, "kc-1")).thenReturn(mapped);

        final UserResponseDto result = service.create(req);

        assertThat(result).isSameAs(mapped);
        final InOrder inOrder = inOrder(keycloakOutboxService, keycloakUserManagementService, userPersistenceService);
        inOrder.verify(keycloakOutboxService).recordCreatePending(req.getEmail());
        inOrder.verify(keycloakUserManagementService).createUser(any(), any(), any(), any(), any());
        inOrder.verify(userPersistenceService).saveNewUser(req, "kc-1");
        inOrder.verify(keycloakOutboxService).markDone(outboxUuid, "kc-1");
    }

    @Test
    void create_dbFailure_compensatesKeycloak_andMarksOutboxFailed() {
        final UUID outboxUuid = UUID.randomUUID();
        when(keycloakOutboxService.recordCreatePending(req.getEmail())).thenReturn(outboxUuid);
        when(keycloakUserManagementService.createUser(any(), any(), any(), any(), any())).thenReturn("kc-2");
        when(userPersistenceService.saveNewUser(req, "kc-2"))
                .thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> service.create(req)).isInstanceOf(DataIntegrityViolationException.class);

        verify(keycloakUserManagementService).deleteUser("kc-2");                 // compensation
        verify(keycloakOutboxService).markFailed(eq(outboxUuid), anyString());
        verify(keycloakOutboxService, never()).markDone(any(), any());
    }
```
(Imports: `org.mockito.InOrder`, `static org.mockito.Mockito.inOrder`, `static org.mockito.Mockito.never`, `org.springframework.dao.DataIntegrityViolationException`, the existing `req` fixture for `UserCreateRequestDto`.)

- [ ] **Step 2: Run — expect FAIL (`recordCreatePending`/`markDone` not wired)**

Run: `JAVA_HOME="C:\Program Files\Java\jdk-26" ./mvnw.cmd test -Dtest=UserManagementServiceImpTest -DfailIfNoTests=false`
Expected: compile failure.

- [ ] **Step 3: Update `UserManagementServiceImp.create` (no change to `UserPersistenceService`)**

Add constructor dep `private final KeycloakOutboxService keycloakOutboxService;` and rewrite:
```java
    @Override
    public UserResponseDto create(final UserCreateRequestDto req) {
        log.info("user creation request received for email domain '{}'", LogSafetyUtils.extractEmailDomain(req.getEmail()));

        // Durable intent FIRST (own TX) — survives a crash so the job can find+compensate an orphan.
        final UUID outboxUuid = keycloakOutboxService.recordCreatePending(req.getEmail());

        // Phase 1 — Keycloak (no TX). Password used in-memory only, never stored.
        final String keycloakId = keycloakUserManagementService.createUser(
                req.getEmail(), req.getFirstName(), req.getLastName(), req.getPassword(), Role.USER);

        // Phase 2 — DB persistence (own REQUIRES_NEW TX). On failure: in-line compensation + mark FAILED.
        final UserResponseDto saved;
        try {
            saved = userPersistenceService.saveNewUser(req, keycloakId);
        } catch (RuntimeException e) {
            try {
                keycloakUserManagementService.deleteUser(keycloakId);   // in-line compensation
            } catch (RuntimeException compensationFailure) {
                log.error("Compensation failed for keycloakId={} — job will reconcile", keycloakId, compensationFailure);
            }
            keycloakOutboxService.markFailed(outboxUuid, e.getMessage());
            throw e;
        }

        // Phase 3 — DB durable: close the breadcrumb. A crash before this leaves it PENDING; the job's
        // reconcileCreate then closes it DONE (Keycloak + DB user both exist) WITHOUT compensating.
        keycloakOutboxService.markDone(outboxUuid, keycloakId);
        return saved;
    }
```
Add `import java.util.UUID;` if absent.

- [ ] **Step 4: Run — expect PASS**

Run: same as Step 2. Expected: PASS (both new tests + the unchanged existing create tests, which now also need the `recordCreatePending` stub — update them to stub it, or set the mock lenient in `@BeforeEach`).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/novatech/cybertech/services/implementation/UserManagementServiceImp.java \
        src/test/java/com/novatech/cybertech/services/implementation/*UserManagementServiceImpTest.java
git commit -m "feat(saga): CREATE writes durable outbox breadcrumb + compensation"
```

---

## Task 5: Wire the DELETE saga (co-commit outbox, repurpose event)

**Files:**
- Modify: `services/implementation/UserManagementServiceImp.java` (`deleteByUUID`, `deleteByUUIDs`), `events/UserDeletedEvent.java`, `events/listener/UserDeletionListener.java`
- Test: existing `UserManagementServiceImpTest` (delete tests) + `UserDeletionListenerTest` (create/update)

- [ ] **Step 1: Write the failing test (delete co-commits an outbox row + event carries its uuid)**

```java
    @Test
    void deleteByUUID_recordsDeleteOutbox_andPublishesEventWithOutboxUuid() {
        final UUID userUuid = UUID.randomUUID();
        final UUID outboxUuid = UUID.randomUUID();
        final UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId("kc-del").build();
        when(userRepository.findByUuid(userUuid)).thenReturn(Optional.of(user));
        when(keycloakOutboxService.recordDeletePending("kc-del")).thenReturn(outboxUuid);

        service.deleteByUUID(userUuid);

        verify(userRepository).deleteByUuid(userUuid);
        verify(keycloakOutboxService).recordDeletePending("kc-del");
        final ArgumentCaptor<UserDeletedEvent> captor = ArgumentCaptor.forClass(UserDeletedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getOutboxUuid()).isEqualTo(outboxUuid);
    }
```

- [ ] **Step 2: Run — expect FAIL**

Run: `JAVA_HOME="C:\Program Files\Java\jdk-26" ./mvnw.cmd test -Dtest=UserManagementServiceImpTest -DfailIfNoTests=false`
Expected: compile failure (`getOutboxUuid`, `recordDeletePending`).

- [ ] **Step 3: Repurpose `UserDeletedEvent` to carry the outbox uuid**

```java
package com.novatech.cybertech.events;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

/**
 * Fired AFTER the local-DB delete + the co-committed DELETE outbox row. The listener reconciles that
 * outbox row immediately (Keycloak delete + mark DONE); if it fails/crashes, the batch job is the
 * durable backstop because the outbox row survived the commit.
 */
@Getter
public class UserDeletedEvent extends ApplicationEvent {

    private final UUID outboxUuid;

    public UserDeletedEvent(final Object source, final UUID outboxUuid) {
        super(source);
        this.outboxUuid = outboxUuid;
    }
}
```

- [ ] **Step 4: Rewrite `UserDeletionListener` to reconcile the co-committed row**

```java
package com.novatech.cybertech.events.listener;

import com.novatech.cybertech.entities.KeycloakOutboxEntity;
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
 * Immediate (AFTER_COMMIT) reconciliation of the DELETE outbox row so the Keycloak identity is revoked
 * promptly. Durability comes from the outbox row itself (co-committed with the DB delete): if this
 * listener fails or the process crashes, the row stays PENDING and the batch job reconciles it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserDeletionListener {

    @Value("${cybertech.keycloak.outbox.max-attempts:5}")
    private int maxAttempts;

    private final KeycloakOutboxRepository outboxRepository;
    private final KeycloakOutboxService keycloakOutboxService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserDeleted(final UserDeletedEvent event) {
        try {
            final KeycloakOutboxEntity row = outboxRepository.findByUuid(event.getOutboxUuid()).orElse(null);
            if (row != null) {
                keycloakOutboxService.reconcile(row, maxAttempts);
            }
        } catch (Exception e) {
            log.error("Immediate reconcile of delete outbox {} failed — batch job will retry",
                    event.getOutboxUuid(), e);
        }
    }
}
```

- [ ] **Step 5: Update `deleteByUUID` / `deleteByUUIDs` in `UserManagementServiceImp`**

```java
    @Override
    @Transactional
    public void deleteByUUID(final UUID uuid) {
        final UserEntity user = userRepository.findByUuid(uuid)
                .orElseThrow(() -> new UserNotFoundException("No user with the UUID: " + uuid + " found"));
        final String keycloakId = user.getKeycloakId();

        userRepository.deleteByUuid(uuid);

        // Co-commit a durable DELETE breadcrumb in THIS transaction, then trigger immediate reconcile.
        final UUID outboxUuid = keycloakOutboxService.recordDeletePending(keycloakId);
        eventPublisher.publishEvent(new UserDeletedEvent(this, outboxUuid));
    }

    @Override
    @Transactional
    public void deleteByUUIDs(final Collection<UUID> uuids) {
        final List<UserEntity> users = userRepository.findAllByUuidIn(uuids);
        userRepository.deleteAllByUuidIn(uuids);
        users.forEach(user -> {
            final UUID outboxUuid = keycloakOutboxService.recordDeletePending(user.getKeycloakId());
            eventPublisher.publishEvent(new UserDeletedEvent(this, outboxUuid));
        });
    }
```

- [ ] **Step 6: Run — expect PASS; fix `UserDeletionListenerTest` to the new shape**

Run: `JAVA_HOME="C:\Program Files\Java\jdk-26" ./mvnw.cmd test -Dtest=UserManagementServiceImpTest,UserDeletionListenerTest -DfailIfNoTests=false`
Expected: PASS. Rewrite any `UserDeletionListenerTest` cases to stub `outboxRepository.findByUuid(...)` + verify `keycloakOutboxService.reconcile(...)` instead of the old `keycloakService.deleteUser`.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/novatech/cybertech/services/implementation/UserManagementServiceImp.java \
        src/main/java/com/novatech/cybertech/events/UserDeletedEvent.java \
        src/main/java/com/novatech/cybertech/events/listener/UserDeletionListener.java \
        src/test/java/com/novatech/cybertech/...
git commit -m "feat(saga): DELETE co-commits durable outbox row, listener reconciles"
```

---

## Task 6: Reconciliation batch job (tasklet + config + scheduler)

**Files:**
- Create: `batch/task/KeycloakOutboxReconciliationTasklet.java`, `batch/job/KeycloakOutboxReconciliationJob.java`
- Modify: `config/BatchConfig.java`, `constants/CyberTechAppConstants.java`, `src/main/resources/application.properties`
- Test: `src/test/java/com/novatech/cybertech/batch/task/KeycloakOutboxReconciliationTaskletTest.java`

- [ ] **Step 1: Add constants**

In `CyberTechAppConstants.java`:
```java
    public static final String KEYCLOAK_OUTBOX_RECONCILIATION_JOB = "KEYCLOAK_OUTBOX_RECONCILIATION_JOB";
```

- [ ] **Step 2: Write the failing tasklet test**

`KeycloakOutboxReconciliationTaskletTest.java`:
```java
package com.novatech.cybertech.batch.task;

import com.novatech.cybertech.entities.KeycloakOutboxEntity;
import com.novatech.cybertech.entities.enums.OutboxOperationType;
import com.novatech.cybertech.entities.enums.OutboxStatus;
import com.novatech.cybertech.repositories.KeycloakOutboxRepository;
import com.novatech.cybertech.services.core.KeycloakOutboxService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KeycloakOutboxReconciliationTaskletTest {

    @Mock KeycloakOutboxRepository outboxRepository;
    @Mock KeycloakOutboxService keycloakOutboxService;

    @InjectMocks KeycloakOutboxReconciliationTasklet tasklet;

    @Test
    void reconcilesEveryStalePendingRow() throws Exception {
        ReflectionTestUtils.setField(tasklet, "stalenessMinutes", 5L);
        ReflectionTestUtils.setField(tasklet, "batchSize", 50);
        ReflectionTestUtils.setField(tasklet, "maxAttempts", 5);
        final KeycloakOutboxEntity row = KeycloakOutboxEntity.builder()
                .operationType(OutboxOperationType.DELETE).status(OutboxStatus.PENDING).keycloakId("kc").build();
        when(outboxRepository.findByStatusAndUpdatedAtBefore(eq(OutboxStatus.PENDING), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(row));

        final RepeatStatus status = tasklet.execute(mock(StepContribution.class),
                new com.novatech.cybertech.batch.base.BaseTasklet.StepArguments("job", LocalDateTime.now(), null, null));

        assertThat(status).isEqualTo(RepeatStatus.FINISHED);
        verify(keycloakOutboxService).reconcile(eq(row), eq(5));
    }
}
```

- [ ] **Step 3: Run — expect FAIL (tasklet missing)**

Run: `JAVA_HOME="C:\Program Files\Java\jdk-26" ./mvnw.cmd test -Dtest=KeycloakOutboxReconciliationTaskletTest -DfailIfNoTests=false`
Expected: compile failure.

- [ ] **Step 4: Implement the tasklet**

`batch/task/KeycloakOutboxReconciliationTasklet.java`:
```java
package com.novatech.cybertech.batch.task;

import com.novatech.cybertech.batch.base.BaseTasklet;
import com.novatech.cybertech.entities.KeycloakOutboxEntity;
import com.novatech.cybertech.entities.enums.OutboxStatus;
import com.novatech.cybertech.repositories.KeycloakOutboxRepository;
import com.novatech.cybertech.services.core.KeycloakOutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Crash-recovery for the Keycloak outbox. Scans rows left {@link OutboxStatus#PENDING} beyond a
 * staleness window (a healthy synchronous request marks its row terminal within milliseconds) and
 * reconciles each idempotently via {@link KeycloakOutboxService#reconcile}. Mirrors
 * {@link RedeliverFailedNotificationsTasklet}: one-shot, paginated, sets COMPLETED + FINISHED.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KeycloakOutboxReconciliationTasklet extends BaseTasklet {

    private final KeycloakOutboxRepository outboxRepository;
    private final KeycloakOutboxService keycloakOutboxService;

    @Value("${cybertech.keycloak.outbox.staleness-minutes:5}")
    private long stalenessMinutes;

    @Value("${cybertech.keycloak.outbox.batch-size:50}")
    private int batchSize;

    @Value("${cybertech.keycloak.outbox.max-attempts:5}")
    private int maxAttempts;

    @Override
    public RepeatStatus execute(final StepContribution stepContribution, final StepArguments stepArguments) {
        final LocalDateTime threshold = LocalDateTime.now().minusMinutes(stalenessMinutes);
        final List<KeycloakOutboxEntity> stale = outboxRepository.findByStatusAndUpdatedAtBefore(
                OutboxStatus.PENDING, threshold, PageRequest.ofSize(batchSize));

        if (stale.isEmpty()) {
            log.info("No stale PENDING keycloak-outbox rows to reconcile");
            stepContribution.setExitStatus(ExitStatus.COMPLETED);
            return RepeatStatus.FINISHED;
        }

        log.info("Reconciling {} stale keycloak-outbox rows", stale.size());
        stale.forEach(row -> keycloakOutboxService.reconcile(row, maxAttempts));

        stepContribution.setExitStatus(ExitStatus.COMPLETED);
        return RepeatStatus.FINISHED;
    }
}
```

- [ ] **Step 5: Run — expect PASS**

Run: same as Step 3. Expected: PASS.

- [ ] **Step 6: Wire Job + Step beans in `BatchConfig`**

Add field `private final KeycloakOutboxReconciliationTasklet keycloakOutboxReconciliationTasklet;`, a private constant `private static final String KEYCLOAK_OUTBOX_RECONCILIATION_TASKLET = "KeycloakOutboxReconciliationTasklet";`, the static import `KEYCLOAK_OUTBOX_RECONCILIATION_JOB`, and:
```java
    @Bean(KEYCLOAK_OUTBOX_RECONCILIATION_JOB)
    public Job keycloakOutboxReconciliationJob() {
        return new JobBuilder(KEYCLOAK_OUTBOX_RECONCILIATION_JOB, jobRepository)
                .start(keycloakOutboxReconciliationStep())
                .build();
    }

    @Bean(KEYCLOAK_OUTBOX_RECONCILIATION_TASKLET)
    public Step keycloakOutboxReconciliationStep() {
        return new StepBuilder(KEYCLOAK_OUTBOX_RECONCILIATION_TASKLET, jobRepository)
                .tasklet(keycloakOutboxReconciliationTasklet, platformTransactionManager)
                .allowStartIfComplete(true)
                .build();
    }
```

- [ ] **Step 7: Create the `@Scheduled` launcher (mirror `RedeliverFailedNotificationsJob`)**

`batch/job/KeycloakOutboxReconciliationJob.java`:
```java
package com.novatech.cybertech.batch.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.JobRestartException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import static com.novatech.cybertech.constants.CyberTechAppConstants.KEYCLOAK_OUTBOX_RECONCILIATION_JOB;

/**
 * Cron scheduler for the Keycloak outbox reconciliation job (crash recovery). The synchronous
 * request path already performs the effect; this drains rows a crash left PENDING.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KeycloakOutboxReconciliationJob {

    @Value("${cybertech.keycloak.outbox.job.activated:true}")
    private boolean activated;

    @Qualifier(KEYCLOAK_OUTBOX_RECONCILIATION_JOB)
    private final Job job;

    private final JobLauncher jobLauncher;

    @Scheduled(cron = "${cybertech.keycloak.outbox.job.cron:0 */15 * * * *}", zone = "UTC")
    public void startJob() {
        if (!activated) {
            return;
        }
        try {
            final LocalDateTime now = LocalDateTime.now();
            jobLauncher.run(job, new JobParametersBuilder().addLocalDateTime("date", now).toJobParameters());
        } catch (JobInstanceAlreadyCompleteException | JobExecutionAlreadyRunningException | JobRestartException |
                 InvalidJobParametersException e) {
            log.error("Error launching keycloak outbox reconciliation job", e);
        }
    }
}
```

- [ ] **Step 8: Add properties**

In `application.properties`:
```properties
cybertech.keycloak.outbox.job.activated=true
cybertech.keycloak.outbox.job.cron=0 */15 * * * *
cybertech.keycloak.outbox.staleness-minutes=5
cybertech.keycloak.outbox.batch-size=50
cybertech.keycloak.outbox.max-attempts=5
```

- [ ] **Step 9: Run the full unit suite — expect green**

Run: `JAVA_HOME="C:\Program Files\Java\jdk-26" ./mvnw.cmd test`
Expected: BUILD SUCCESS, 0 failures (ArchUnit included).

- [ ] **Step 10: Commit**

```bash
git add src/main/java/com/novatech/cybertech/batch/task/KeycloakOutboxReconciliationTasklet.java \
        src/main/java/com/novatech/cybertech/batch/job/KeycloakOutboxReconciliationJob.java \
        src/main/java/com/novatech/cybertech/config/BatchConfig.java \
        src/main/java/com/novatech/cybertech/constants/CyberTechAppConstants.java \
        src/main/resources/application.properties \
        src/test/java/com/novatech/cybertech/batch/task/KeycloakOutboxReconciliationTaskletTest.java
git commit -m "feat(saga): scheduled batch job reconciles keycloak outbox"
```

---

## Task 7: Integration test (Testcontainers)

**Files:**
- Create: `src/test/java/com/novatech/cybertech/integration/user/KeycloakOutboxFlowIT.java`

- [ ] **Step 1: Write the IT — register happy path leaves a DONE outbox row**

Model on `UserRegistrationFlowIT` (stubs `KeycloakUserManagementService`; `@Testcontainers`, `@ActiveProfiles("test")`, `@Import(TestcontainersConfiguration.class)`):
```java
    @Test
    @DisplayName("register happy path → user persisted AND outbox CREATE row is DONE")
    void registerWritesDoneOutboxRow() throws Exception {
        // stub keycloak.createUser -> "kc-it"; perform POST /register; expect 201
        // then:
        final List<KeycloakOutboxEntity> rows = keycloakOutboxRepository.findAll();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getOperationType()).isEqualTo(OutboxOperationType.CREATE);
        assertThat(rows.get(0).getStatus()).isEqualTo(OutboxStatus.DONE);
    }
```

- [ ] **Step 2: Write the IT — orphan reconciliation compensates**

```java
    @Test
    @DisplayName("stale PENDING CREATE with a Keycloak orphan → tasklet compensates (deletes) and marks FAILED")
    void taskletCompensatesOrphan() {
        // 1) insert a PENDING CREATE outbox row for email X with updatedAt far in the past
        // 2) stub keycloakService.searchByEmail(X) -> Optional.of("kc-orphan")
        //    and userRepository has NO user with kc-orphan
        // 3) run the tasklet (inject KeycloakOutboxReconciliationTasklet; call execute(...))
        // 4) verify keycloakService.deleteUser("kc-orphan") was called and the row is FAILED
        verify(keycloakUserManagementService).deleteUser("kc-orphan");
        assertThat(keycloakOutboxRepository.findByUuid(rowUuid).orElseThrow().getStatus())
                .isEqualTo(OutboxStatus.FAILED);
    }
```
> The forcing of a past `updatedAt` may require a native update or saving then back-dating via the repository; if `@LastModifiedDate` overwrites it, query with a `threshold` in the future instead (as the unit repo test does) to select the row deterministically.

- [ ] **Step 3: Run the IT**

Run (Docker up):
```
JAVA_HOME="C:\Program Files\Java\jdk-26" ./mvnw.cmd verify -Pintegration-test -Dit.test=KeycloakOutboxFlowIT -Dtest=ZZZ -Dsurefire.failIfNoSpecifiedTests=false -DfailIfNoTests=false -Djacoco.skip=true
```
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add src/test/java/com/novatech/cybertech/integration/user/KeycloakOutboxFlowIT.java
git commit -m "test(saga): IT for keycloak outbox create-DONE + orphan compensation"
```

---

## Final verification

- [ ] Full unit suite green: `JAVA_HOME="C:\Program Files\Java\jdk-26" ./mvnw.cmd test`
- [ ] Targeted ITs green (Docker up): `… verify -Pintegration-test -Dit.test=KeycloakOutboxFlowIT,UserRegistrationFlowIT …`
- [ ] No leftover references to the old `UserDeletedEvent(this, keycloakId)` constructor (search `new UserDeletedEvent`).
- [ ] Spec requirements covered: durable CREATE breadcrumb (T4), DELETE co-commit + immediate reconcile (T5), no-password-at-rest job (T3/T6), FAILED+attempts-as-DLB (T3), no separate DLB table (design), UPDATE untouched (verify `update` unchanged).
