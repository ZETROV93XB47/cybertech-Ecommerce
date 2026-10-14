package com.novatech.cybertech.repositories;

import com.novatech.cybertech.entities.PushSubscriptionEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PushSubscriptionRepository extends CrudBaseRepository<PushSubscriptionEntity, Long> {

    Optional<PushSubscriptionEntity> findByEndpointHash(String endpointHash);

    List<PushSubscriptionEntity> findAllByUserEntity_KeycloakId(String keycloakId);

    boolean existsByUserEntity_KeycloakId(String keycloakId);

    long deleteByUuidAndUserEntity_KeycloakId(UUID uuid, String keycloakId);
}
