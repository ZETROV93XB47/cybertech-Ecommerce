package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.dto.request.user.UserCreateRequestDto;
import com.novatech.cybertech.dto.request.user.UserUpdateRequestDto;
import com.novatech.cybertech.dto.response.user.UserResponseDto;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.entities.enums.Role;
import com.novatech.cybertech.events.UserDeletedEvent;
import com.novatech.cybertech.exceptions.UserNotFoundException;
import com.novatech.cybertech.mappers.entity.UserMapper;
import com.novatech.cybertech.repositories.UserRepository;
import com.novatech.cybertech.services.core.UserManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
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
        log.info("user creation request : {}", req);

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
     * Update a user's profile across the local DB and Keycloak without coupling them in a single
     * outer transaction (Bug 3 — Option B, mirrors the Wave 2 H1 pattern applied to {@link #create}).
     *
     * <p>Phase 1 (DB, REQUIRES_NEW): {@link UserPersistenceService#updateUser} patches the local
     * entity and saves it under its own transaction. If the SQL fails, the inner TX rolls back
     * and we propagate without ever calling Keycloak — no orphan Keycloak update.
     *
     * <p>Phase 2 (Keycloak, NO transaction): once the DB update is durable we propagate the change
     * to Keycloak. If this throws we cannot roll back the DB write, so we log loudly and surface
     * a {@link RuntimeException} to the caller so they know a manual reconciliation is needed.
     */
    @Override
    public UserResponseDto update(final UserUpdateRequestDto userUpdateRequestDto) {
        final UserEntity loadedUser = userRepository.findByUuid(userUpdateRequestDto.getUuid())
                .orElseThrow(() -> new UserNotFoundException("No user with the UUID: " + userUpdateRequestDto.getUuid() + " found"));

        // Phase 1 — DB first under its own REQUIRES_NEW transaction.
        final UserResponseDto saved = userPersistenceService.updateUser(userUpdateRequestDto, loadedUser);

        // Phase 2 — push to Keycloak (no transaction). On failure: log + propagate so the API
        // surfaces the desync to the caller for manual reconciliation. No KeycloakSyncException
        // type exists yet — wrapping in a plain RuntimeException keeps the change minimal.
        try {
            keycloakUserManagementService.updateUser(loadedUser.getKeycloakId(), userUpdateRequestDto);
        } catch (Exception kce) {
            log.error("CRITICAL: DB updated but Keycloak update failed for user {} — manual reconciliation required",
                    loadedUser.getKeycloakId(), kce);
            throw new RuntimeException("Keycloak sync failed after DB update: " + kce.getMessage(), kce);
        }

        return saved;
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

    @Override
    @Transactional
    public void deleteByUUIDs(final Collection<UUID> uuids) {
        List<UserEntity> users = userRepository.findAllByUuidIn(uuids);

        users.forEach(user -> keycloakUserManagementService.deleteUser(user.getKeycloakId()));
        userRepository.deleteAllByUuidIn(uuids);
    }

    @Transactional
    public Collection<UserResponseDto> createAutomatically(Collection<UserEntity> users) {
        return new ArrayList<>(userMapper.mapFromEntityToResponseDto(users.stream().map(userRepository::save).toList()));
    }
}
