package com.novatech.cybertech.services.core;

import com.novatech.cybertech.dto.request.user.UserCreateRequestDto;
import com.novatech.cybertech.dto.request.user.UserUpdateRequestDto;
import com.novatech.cybertech.dto.response.user.UserResponseDto;
import com.novatech.cybertech.entities.UserEntity;

/**
 * Persistence-only collaborator for the user Keycloak/DB saga.
 *
 * <p>Kept separate from {@code UserManagementService} so the SQL write (and the bank-card
 * creation, which must share the same SQL transaction) runs in its own
 * {@code Propagation.REQUIRES_NEW} transaction, isolated from the orchestrator's Keycloak call —
 * see {@code UserManagementServiceImp} and {@code KeycloakOutboxServiceImp} for the callers.
 */
public interface UserPersistenceService {

    /** Persist a newly-created {@link UserEntity} (and optional bank card) under a fresh transaction. */
    UserResponseDto saveNewUser(UserCreateRequestDto req, String keycloakId);

    /** Persist a patch onto an already-loaded {@link UserEntity} under a fresh transaction. */
    UserResponseDto updateUser(UserUpdateRequestDto dto, UserEntity loadedUser);
}
