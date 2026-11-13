package com.novatech.cybertech.services.core;

import com.novatech.cybertech.dto.request.user.UserUpdateRequestDto;
import com.novatech.cybertech.entities.enums.Role;

import java.util.Optional;

/**
 * Keycloak admin-client facade for the user saga (CREATE/UPDATE/DELETE), plus the reconciliation
 * lookup used by the outbox job.
 *
 * @see UserPersistenceService
 * @see KeycloakOutboxService
 */
public interface KeycloakUserManagementService {

    /** Creates the Keycloak user, assigns the realm role, and returns the new Keycloak id. */
    String createUser(String email, String firstName, String lastName, String rawPassword, Role role);

    /** Idempotent (404-tolerant) delete — safe to re-issue from the outbox reconciliation job. */
    void deleteUser(String keycloakUserId);

    /** Reconciliation lookup: resolves a Keycloak user id by exact email, or empty if none. */
    Optional<String> searchByEmail(String email);

    /** Propagates the non-null fields of the patch onto the Keycloak user representation. */
    void updateUser(String keycloakId, UserUpdateRequestDto userUpdateRequestDto);
}
