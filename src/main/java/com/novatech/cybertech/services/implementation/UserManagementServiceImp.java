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

    /**
     * Create a user across Keycloak and the local DB without coupling them in a single TX.
     *
     * <p>Phase 1 (no transaction): create the user in Keycloak. If this throws, abort — there is
     * nothing to compensate.
     *
     * <p>Phase 2 (REQUIRES_NEW transaction inside {@link UserPersistenceService}): persist the
     * local entity (and optional bank card). If this throws, the inner TX rolls back the SQL
     * work cleanly. We then run a compensating Keycloak {@code deleteUser} from OUTSIDE any
     * transactional context. If compensation itself fails we log loudly so an operator can
     * reconcile manually — and we still rethrow the original cause to the caller.
     */
    @Override
    public UserResponseDto create(final UserCreateRequestDto req) {
        // FIX(PII-LEAK): the full request DTO contains the raw password, bank-card details and email —
        // log only the email domain so traffic patterns stay observable without leaking PII into log appenders.
        log.info("user creation request received for email domain '{}'", LogSafetyUtils.extractEmailDomain(req.getEmail()));

        // Phase 1 — Keycloak only, NO transaction. A failure here is terminal: no compensation needed.
        final String keycloakId = keycloakUserManagementService.createUser(
                req.getEmail(), req.getFirstName(), req.getLastName(), req.getPassword(), Role.USER);
        log.info("Keycloak id : {}", keycloakId);

        // Phase 2 — DB persistence in its own REQUIRES_NEW transaction; compensate if it fails.
        try {
            return userPersistenceService.saveNewUser(req, keycloakId);
        } catch (RuntimeException e) {
            try {
                keycloakUserManagementService.deleteUser(keycloakId);
            } catch (RuntimeException compensationFailure) {
                log.error("Compensation failed for keycloakId={} — manual reconciliation required",
                        keycloakId, compensationFailure);
            }
            throw e;
        }
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
     * outer transaction (Bug 3 — Option B: Keycloak FIRST).
     *
     * <p>Phase 1 (Keycloak, NO transaction): propagate the change to Keycloak first. Keycloak
     * rejections (e.g. a duplicate email in the realm or a profile-policy violation) are the
     * COMMON failure mode for a profile update; calling Keycloak before any DB write means a
     * rejection aborts here with <i>neither</i> system mutated — no silent desync.
     *
     * <p>Phase 2 (DB, REQUIRES_NEW): {@link UserPersistenceService#updateUser} patches the local
     * entity and saves it under its own transaction. A failure here is rare (a simple update of an
     * already-loaded row) but would leave Keycloak ahead of the DB — we log loudly and propagate so
     * the caller knows a manual reconciliation is needed.
     */
    @Override
    public UserResponseDto update(final UserUpdateRequestDto userUpdateRequestDto) {
        final UserEntity loadedUser = userRepository.findByUuid(userUpdateRequestDto.getUuid())
                .orElseThrow(() -> new UserNotFoundException("No user with the UUID: " + userUpdateRequestDto.getUuid() + " found"));

        // Phase 1 — Keycloak first (no transaction). A rejection here aborts before any DB write.
        keycloakUserManagementService.updateUser(loadedUser.getKeycloakId(), userUpdateRequestDto);

        // Phase 2 — DB write under its own REQUIRES_NEW transaction. Rare failure → Keycloak is
        // ahead of the DB; log loudly and propagate for manual reconciliation.
        try {
            return userPersistenceService.updateUser(userUpdateRequestDto, loadedUser);
        } catch (RuntimeException dbFailure) {
            log.error("CRITICAL: Keycloak updated but DB write failed for user {} — manual reconciliation required",
                    loadedUser.getKeycloakId(), dbFailure);
            throw dbFailure;
        }
    }

    /**
     * Delete a user. DB delete runs inside the method's transaction; the Keycloak delete is
     * deferred to {@link com.novatech.cybertech.events.listener.UserDeletionListener} via a
     * {@link UserDeletedEvent} that listener consumes under
     * {@code TransactionPhase.AFTER_COMMIT} (Bug 4). This guarantees Keycloak is only touched
     * once the SQL row is durably gone — a rollback (e.g. FK constraint) will simply not fire
     * the listener at all.
     */
    @Override
    @Transactional
    public void deleteByUUID(final UUID uuid) {
        final UserEntity user = userRepository.findByUuid(uuid).orElseThrow(() -> new UserNotFoundException("No user with the UUID: " + uuid + " found"));
        final String keycloakId = user.getKeycloakId();

        userRepository.deleteByUuid(uuid);

        // AFTER_COMMIT listener picks this up and calls Keycloak.deleteUser only when the
        // surrounding TX has actually committed.
        eventPublisher.publishEvent(new UserDeletedEvent(this, keycloakId));
    }

    /**
     * Bulk delete that mirrors the {@link #deleteByUUID} AFTER_COMMIT pattern (Bug 4).
     *
     * <p>FIX(SAGA-INCONSISTENCY): the previous implementation called Keycloak inside the
     * surrounding transaction <i>before</i> issuing the SQL delete. A late SQL rollback (FK
     * violation, unique constraint, ...) therefore left orphan Keycloak deletions for rows that
     * still existed in the DB — the exact anti-pattern the singular {@code deleteByUUID} path
     * already fixes via {@link UserDeletedEvent}. We mirror that fix here: publish one event per
     * resolved entity and let {@code UserDeletionListener} drain them under
     * {@code TransactionPhase.AFTER_COMMIT} so Keycloak only fires once the SQL bulk delete has
     * actually committed.
     */
    @Override
    @Transactional
    public void deleteByUUIDs(final Collection<UUID> uuids) {
        final List<UserEntity> users = userRepository.findAllByUuidIn(uuids);

        userRepository.deleteAllByUuidIn(uuids);
        users.forEach(user -> eventPublisher.publishEvent(new UserDeletedEvent(this, user.getKeycloakId())));
    }

    /**
     * Frontend-gap #3 — self-service update path. Resolves the caller's entity via the JWT
     * subject ({@code keycloakId}), then reuses the same Phase-DB-then-Keycloak choreography
     * as {@link #update(UserUpdateRequestDto)}: SQL write inside the persistence service's
     * REQUIRES_NEW TX, Keycloak update outside any TX, with a clean rethrow when the Keycloak
     * step fails after a durable DB commit.
     *
     * <p>The {@link UserSelfUpdateRequestDto} is mapped onto a transient
     * {@link UserUpdateRequestDto} carrying only the four fields a user is allowed to mutate
     * on themselves. We deliberately do NOT propagate the Keycloak email change here — the
     * self DTO has no email field — but we do propagate first/last name to keep Keycloak
     * profile data in sync with the DB row (mirrors the same fields {@code KeycloakUserManagementService.updateUser}
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

        // Phase 1 — Keycloak first (no transaction). Same Option B ordering as #update: a Keycloak
        // rejection aborts before any DB write so the two systems can't silently diverge.
        keycloakUserManagementService.updateUser(loadedUser.getKeycloakId(), adapted);

        // Phase 2 — DB write under its own REQUIRES_NEW transaction.
        final UserResponseDto saved;
        try {
            saved = userPersistenceService.updateUser(adapted, loadedUser);
        } catch (RuntimeException dbFailure) {
            log.error("CRITICAL: Keycloak updated but DB write failed for user {} — manual reconciliation required",
                    loadedUser.getKeycloakId(), dbFailure);
            throw dbFailure;
        }

        // Phone number isn't on UserUpdateRequestDto — patch directly when supplied. No
        // Keycloak side effect: phone is not synced through KeycloakUserManagementService.
        if (dto.getPhoneNumber() != null) {
            loadedUser.setPhoneNumber(dto.getPhoneNumber());
            userRepository.save(loadedUser);
        }

        return saved;
    }
}
