package com.novatech.cybertech.repositories;

import com.novatech.cybertech.entities.UserEntity;

import java.util.Optional;

public interface UserRepository extends CrudBaseRepository<UserEntity, Long> {
    Optional<UserEntity> findByEmail(final String email);

    Optional<UserEntity> findByKeycloakId(final String keycloakId);

    Optional<UserEntity> findByKeycloakIdAndIsActive(final String keycloakId, final boolean isActive);

    boolean existsByKeycloakIdAndIsActive(final String keycloakId, final boolean isActive);

    boolean existsByKeycloakId(String keycloakId);
}
