package com.novatech.cybertech.services.core;

import com.novatech.cybertech.dto.request.user.UserCreateRequestDto;
import com.novatech.cybertech.dto.request.user.UserSelfUpdateRequestDto;
import com.novatech.cybertech.dto.request.user.UserUpdateRequestDto;
import com.novatech.cybertech.dto.response.user.UserResponseDto;
import com.novatech.cybertech.entities.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.UUID;

public interface UserManagementService extends CrudBaseService<UUID, UserCreateRequestDto, UserUpdateRequestDto, UserResponseDto> {

    // FIX(INTERFACE-CONTRACT): added missing method to honor interface-first convention
    /**
     * Paginated read of every user, used by the admin user-management screen.
     *
     * @param pageable Spring Data pagination + sort hint.
     * @return one page of {@link UserResponseDto}.
     */
    Page<UserResponseDto> getAll(final Pageable pageable);

    // FIX(INTERFACE-CONTRACT): added missing method to honor interface-first convention
    /**
     * Developer / load-test utility — bulk-create synthetic users straight into the DB.
     *
     * <p>Skips the Keycloak provisioning saga used by {@link #create(UserCreateRequestDto)};
     * intended exclusively for the admin {@code POST /register/auto} endpoint. The returned
     * collection mirrors the persisted rows.</p>
     *
     * @param users pre-built {@link UserEntity} graph to persist.
     * @return the persisted users mapped to {@link UserResponseDto}.
     */
    Collection<UserResponseDto> createAutomatically(final Collection<UserEntity> users);


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
}
