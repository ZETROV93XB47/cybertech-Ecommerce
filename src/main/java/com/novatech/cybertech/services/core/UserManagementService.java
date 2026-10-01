package com.novatech.cybertech.services.core;

import com.novatech.cybertech.dto.request.user.UserCreateRequestDto;
import com.novatech.cybertech.dto.request.user.UserSelfUpdateRequestDto;
import com.novatech.cybertech.dto.request.user.UserUpdateRequestDto;
import com.novatech.cybertech.dto.response.user.UserResponseDto;
import com.novatech.cybertech.entities.enums.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface UserManagementService extends CrudBaseService<UUID, UserCreateRequestDto, UserUpdateRequestDto, UserResponseDto, String> {

    // Added missing method to honor interface-first convention
    /**
     * Paginated read of every user, used by the admin user-management screen.
     *
     * @param pageable Spring Data pagination + sort hint.
     * @return one page of {@link UserResponseDto}.
     */
    Page<UserResponseDto> getAll(final Pageable pageable);

    /**
     * Frontend-gap #3 — self-service profile update for the authenticated user.
     *
     * <p>The caller's identity is resolved upstream from the JWT subject and passed in via
     * {@code keycloakId}; the entity is loaded by that key. The DTO carries the strict subset
     * of fields a user is allowed to mutate on themselves (first/last name, phone, address) —
     * status / role / email are intentionally left out of {@link UserSelfUpdateRequestDto}.</p>
     *
     * <p>Implementations follow the same Phase-DB-then-Keycloak pattern used by
     * {@link #update(UserUpdateRequestDto)}: DB write inside its own REQUIRES_NEW TX,
     * Keycloak propagation outside any TX, with a {@link RuntimeException} surfaced when the
     * Keycloak step fails after a successful DB write so the caller can react.</p>
     *
     * @param keycloakId the JWT subject of the authenticated caller.
     * @param dto        the self-update payload; nulls leave the field untouched.
     * @return the updated user mapped to {@link UserResponseDto}.
     */
    UserResponseDto updateMe(final String keycloakId, final UserSelfUpdateRequestDto dto);

    /**
     * Admin-only role change (USER &lt;-&gt; ADMIN). Reuses the exact same Keycloak-first-then-DB
     * outbox saga as {@link #update(UserUpdateRequestDto, String)} — see
     * {@code docs/superpowers/specs/2026-06-04-keycloak-outbox-design.md} — so a crash mid-change
     * is recovered the same way any other profile update is: the breadcrumb lets the
     * reconciliation job re-apply the role to whichever system fell behind.
     *
     * <p>Bootstrapping the very first admin is NOT this method's job — it requires calling it,
     * which requires an existing admin JWT, which does not exist yet on a fresh realm. That one
     * account has to be promoted by hand in the Keycloak admin console (Users → Role mapping).
     * This endpoint is for every promotion after that.</p>
     *
     * @param userUuid         the user whose role is being changed.
     * @param role             the new role.
     * @param callerKeycloakId acting admin's identity — audit-logged, not otherwise used.
     * @return the updated user mapped to {@link UserResponseDto}.
     * @throws com.novatech.cybertech.exceptions.UserNotFoundException when no user matches {@code userUuid}.
     */
    UserResponseDto updateRole(final UUID userUuid, final Role role, final String callerKeycloakId);
}
