package com.novatech.cybertech.repositories;

import com.novatech.cybertech.entities.BankCardEntity;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BankCardRepository extends CrudBaseRepository<BankCardEntity, Long> {

    /**
     * Returns every card belonging to a given user. The domain model enforces one card per
     * user ({@code UserEntity.bankCardEntity} is a {@code @OneToOne}), so this resolves to at
     * most one element in practice, but the list shape keeps the query reusable regardless.
     *
     * @param keycloakId the authenticated user's Keycloak subject identifier.
     * @return all cards owned by that user (possibly empty).
     */
    List<BankCardEntity> findAllByUserEntity_KeycloakId(String keycloakId);
}
