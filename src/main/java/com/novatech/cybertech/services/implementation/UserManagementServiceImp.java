package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.dto.request.user.UserCreateRequestDto;
import com.novatech.cybertech.dto.request.user.UserSelfUpdateRequestDto;
import com.novatech.cybertech.dto.request.user.UserUpdateRequestDto;
import com.novatech.cybertech.dto.response.user.UserResponseDto;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.entities.enums.Role;
import com.novatech.cybertech.events.UserDeletedEvent;
import com.novatech.cybertech.exceptions.UserNotFoundException;
import com.novatech.cybertech.mappers.entity.UserMapper;
import com.novatech.cybertech.repositories.UserRepository;
import com.novatech.cybertech.services.core.KeycloakOutboxService;
import com.novatech.cybertech.services.core.UserManagementService;
import com.novatech.cybertech.utils.LogSafetyUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserManagementServiceImp implements UserManagementService {

    private final UserMapper userMapper;
    private final UserRepository userRepository;
    private final KeycloakUserManagementService keycloakUserManagementService;
    private final UserPersistenceService userPersistenceService;
    private final ApplicationEventPublisher eventPublisher;
    private final KeycloakOutboxService keycloakOutboxService;

    /**
     * Create a user across Keycloak and the local DB without coupling them in a single TX —
     * now crash-safe via a durable outbox breadcrumb (see
     * {@code docs/superpowers/specs/2026-06-04-keycloak-outbox-design.md}).
     *
     * <p>Phase 0 (REQUIRES_NEW inside {@link KeycloakOutboxService}): commit a PENDING CREATE
     * intent keyed by email. If the process crashes anywhere after this point, the
     * reconciliation job finds the stale row, looks the email up in Keycloak and compensates
     * any orphan — the breadcrumb is what makes the crash window recoverable.
     *
     * <p>Phase 1 (no transaction): create the user in Keycloak. The raw password lives in
     * memory only for the duration of this call — it is never written to the outbox row.
     *
     * <p>Phase 2 (REQUIRES_NEW inside {@link UserPersistenceService}): persist the local entity
     * (and optional bank card). On failure: in-line compensating {@code deleteUser} + the row is
     * marked terminal FAILED (clean failure — the caller retries), original cause rethrown.
     *
     * <p>Phase 3: mark the row DONE. A crash between Phase 2 and here leaves it PENDING — the
     * job then sees Keycloak + DB both populated and closes it DONE without compensating.
     */
    @Override
    public UserResponseDto create(final UserCreateRequestDto req) {
        // FIX(PII-LEAK): the full request DTO contains the raw password, bank-card details and email —
        // log only the email domain so traffic patterns stay observable without leaking PII into log appenders.
        log.info("user creation request received for email domain '{}'", LogSafetyUtils.extractEmailDomain(req.getEmail()));

        // Phase 0 — durable intent FIRST (own TX): survives a crash so the job can find + compensate an orphan.
        final UUID outboxUuid = keycloakOutboxService.recordCreatePending(req.getEmail());

        // Phase 1 — Keycloak only, NO transaction. A failure here is terminal: no compensation needed.
        final String keycloakId;
        try {
            keycloakId = keycloakUserManagementService.createUser(req.getEmail(), req.getFirstName(), req.getLastName(), req.getPassword(), Role.USER);
        } catch (RuntimeException keycloakFailure) {
            keycloakOutboxService.markFailed(outboxUuid, keycloakFailure.getMessage());
            throw keycloakFailure;
        }
        log.info("Keycloak id : {}", keycloakId);

        // Phase 2 — DB persistence in its own REQUIRES_NEW transaction; compensate if it fails.
        final UserResponseDto saved;
        try {
            saved = userPersistenceService.saveNewUser(req, keycloakId);
        } catch (RuntimeException e) {
            try {
                keycloakUserManagementService.deleteUser(keycloakId);
            } catch (RuntimeException compensationFailure) {
                log.error("Compensation failed for keycloakId={} — outbox job will reconcile",
                        keycloakId, compensationFailure);
            }
            keycloakOutboxService.markFailed(outboxUuid, e.getMessage());
            throw e;
        }

        // Phase 3 — close the breadcrumb. A crash before this leaves it PENDING; the job's
        // reconcileCreate then closes it DONE (Keycloak + DB user both exist) WITHOUT compensating.
        keycloakOutboxService.markDone(outboxUuid, keycloakId);
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public Collection<UserResponseDto> getAll() {
        return userMapper.mapFromEntityToResponseDto(userRepository.findAll());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponseDto> getAll(final Pageable pageable) {
        return userRepository.findAll(pageable).map(userMapper::mapFromEntityToResponseDto);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponseDto getByUUID(final UUID uuid) {
        final UserEntity user = userRepository.findByUuid(uuid).orElseThrow(() -> new UserNotFoundException("No user with the UUID: " + uuid + " found"));
        return userMapper.mapFromEntityToResponseDto(user);
    }

    @Override
    @Transactional(readOnly = true)
    public Collection<UserResponseDto> getByUUIDs(final Collection<UUID> uuids) {
        return userMapper.mapFromEntityToResponseDto(userRepository.findAllByUuidIn(uuids));
    }

    /**
     * Update a user's profile across Keycloak and the local DB without coupling them in a single
     * outer transaction (Bug 3 — Option B: Keycloak FIRST) — now crash-safe via a durable outbox
     * breadcrumb carrying the patch payload (forward recovery; no secret involved).
     *
     * <p>See {@link #updateWithOutbox} for the phase choreography.
     */
    @Override
    public UserResponseDto update(final UserUpdateRequestDto userUpdateRequestDto) {
        final UserEntity loadedUser = userRepository.findByUuid(userUpdateRequestDto.getUuid())
                .orElseThrow(() -> new UserNotFoundException("No user with the UUID: " + userUpdateRequestDto.getUuid() + " found"));

        return updateWithOutbox(userUpdateRequestDto, loadedUser);
    }

    /**
     * Shared UPDATE saga used by {@link #update} and {@link #updateMe}.
     *
     * <p>Phase 0 (REQUIRES_NEW inside {@link KeycloakOutboxService}): commit a PENDING UPDATE
     * intent carrying the JSON-serialized patch. Unlike CREATE, an update is re-applicable
     * without any secret, so a crash anywhere after this point is recovered FORWARD by the
     * reconciliation job (payload re-applied idempotently to Keycloak AND the DB).
     *
     * <p>Phase 1 (Keycloak, NO transaction): Keycloak first — a rejection (duplicate realm email,
     * profile-policy violation: the COMMON failure mode) aborts with neither system mutated; the
     * row is marked terminal FAILED per the synchronous contract (the caller simply retries).
     *
     * <p>Phase 2 (DB, REQUIRES_NEW): a failure here leaves Keycloak ahead of the DB. The row
     * deliberately STAYS PENDING — the job converges the DB side by re-applying the payload,
     * which is precisely the divergence this outbox exists to close.
     */
    private UserResponseDto updateWithOutbox(final UserUpdateRequestDto dto, final UserEntity loadedUser) {
        final String keycloakId = loadedUser.getKeycloakId();

        // Phase 0 — durable breadcrumb FIRST (own TX).
        final UUID outboxUuid = keycloakOutboxService.recordUpdatePending(keycloakId, dto);

        // Phase 1 — Keycloak first (no transaction). A clean rejection aborts before any DB write:
        // neither system mutated → terminal FAILED row, caller retries.
        try {
            keycloakUserManagementService.updateUser(keycloakId, dto);
        } catch (RuntimeException keycloakRejection) {
            keycloakOutboxService.markFailed(outboxUuid, keycloakRejection.getMessage());
            throw keycloakRejection;
        }

        // Phase 2 — DB write under its own REQUIRES_NEW transaction. On failure the row STAYS
        // PENDING (no markFailed): the reconciliation job re-applies the payload to converge.
        final UserResponseDto saved;
        try {
            saved = userPersistenceService.updateUser(dto, loadedUser);
        } catch (RuntimeException dbFailure) {
            log.error("CRITICAL: Keycloak updated but DB write failed for user {} — outbox row {} left PENDING for re-application",
                    keycloakId, outboxUuid, dbFailure);
            throw dbFailure;
        }

        keycloakOutboxService.markDone(outboxUuid, keycloakId);
        return saved;
    }

    /**
     * Delete a user — crash-safe via a DELETE outbox row CO-COMMITTED with the SQL delete.
     *
     * <p>The previous design published an in-memory {@link UserDeletedEvent} carrying the
     * keycloakId; a crash between the DB commit and the AFTER_COMMIT listener lost the intent
     * forever (Keycloak account left able to authenticate). The outbox row now rides THE SAME
     * transaction as the delete: either both commit or neither does. The event only carries the
     * row's uuid so the listener can reconcile it immediately (happy path); if the listener
     * fails or the process dies, the reconciliation job is the durable backstop.
     */
    @Override
    @Transactional
    public void deleteByUUID(final UUID uuid) {
        final UserEntity user = userRepository.findByUuid(uuid).orElseThrow(() -> new UserNotFoundException("No user with the UUID: " + uuid + " found"));
        final String keycloakId = user.getKeycloakId();

        userRepository.deleteByUuid(uuid);

        // Co-commit a durable DELETE breadcrumb in THIS transaction, then trigger immediate reconcile.
        final UUID outboxUuid = keycloakOutboxService.recordDeletePending(keycloakId);
        eventPublisher.publishEvent(new UserDeletedEvent(this, outboxUuid));
    }

    /**
     * Bulk delete mirroring {@link #deleteByUUID}: one co-committed outbox row + one event per
     * resolved entity, all riding the surrounding transaction (a rollback drops the rows AND
     * suppresses the events together).
     */
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

    /**
     * Frontend-gap #3 — self-service update path. Resolves the caller's entity via the JWT
     * subject ({@code keycloakId}), then reuses the shared {@link #updateWithOutbox} saga
     * (breadcrumb → Keycloak → DB → DONE) with a transient {@link UserUpdateRequestDto}
     * carrying only the fields a user is allowed to mutate on themselves.
     *
     * <p>We deliberately do NOT propagate a Keycloak email change here — the self DTO has no
     * email field — but we do propagate first/last name to keep Keycloak profile data in sync
     * with the DB row (mirrors the same fields {@code KeycloakUserManagementService.updateUser}
     * already syncs in the admin path).</p>
     */
    @Override
    public UserResponseDto updateMe(final String keycloakId, final UserSelfUpdateRequestDto dto) {
        final UserEntity loadedUser = userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new UserNotFoundException("No user with the keycloakId: " + keycloakId + " found"));

        final UserUpdateRequestDto adapted = new UserUpdateRequestDto();
        adapted.setUuid(loadedUser.getUuid());
        adapted.setFirstName(dto.getFirstName());
        adapted.setLastName(dto.getLastName());
        adapted.setAddress(dto.getAddress());

        final UserResponseDto saved = updateWithOutbox(adapted, loadedUser);

        // Phone number isn't on UserUpdateRequestDto — patch directly when supplied. No
        // Keycloak side effect: phone is not synced through KeycloakUserManagementService.
        if (dto.getPhoneNumber() != null) {
            loadedUser.setPhoneNumber(dto.getPhoneNumber());
            userRepository.save(loadedUser);
        }

        return saved;
    }
}
