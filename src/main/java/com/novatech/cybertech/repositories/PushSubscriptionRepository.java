package com.novatech.cybertech.repositories;

import com.novatech.cybertech.entities.PushSubscriptionEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PushSubscriptionRepository extends CrudBaseRepository<PushSubscriptionEntity, Long> {

    Optional<PushSubscriptionEntity> findByEndpointHash(String endpointHash);

    /** Oldest first, so the subscription limit evicts the least recently registered browser. */
    List<PushSubscriptionEntity> findAllByUserEntity_KeycloakIdOrderByCreatedAtAsc(String keycloakId);

    Optional<PushSubscriptionEntity> findByUuidAndUserEntity_KeycloakId(UUID uuid, String keycloakId);
}
