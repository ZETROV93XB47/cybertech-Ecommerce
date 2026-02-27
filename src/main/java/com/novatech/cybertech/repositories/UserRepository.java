package com.novatech.cybertech.repositories;

import com.novatech.cybertech.entities.UserEntity;

import java.util.Optional;

public interface UserRepository extends CrudBaseRepository<UserEntity, Long> {
    Optional<UserEntity> findByEmail(final String email);

    Optional<UserEntity> findByKeycloakId(final String keycloakId);

    boolean existsByKeycloakId(String keycloakId);
}
