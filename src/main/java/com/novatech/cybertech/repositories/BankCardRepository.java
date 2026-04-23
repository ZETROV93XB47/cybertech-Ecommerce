package com.novatech.cybertech.repositories;

import com.novatech.cybertech.entities.BankCardEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BankCardRepository extends CrudBaseRepository<BankCardEntity, Long> {

    /**
     * BUG-038: resolves the card currently flagged as the user's default, if any.
     *
     * <p>Spring Data derives the SQL from the method name:
     * {@code userEntity.keycloakId = ?1 AND isDefault = true}. Scoping by {@code keycloakId}
     * (not {@code userUuid}) lets controllers pass the JWT subject straight through with no
     * extra user lookup.</p>
     *
     * @param keycloakId the authenticated user's Keycloak subject identifier.
     * @return the default card if one is set, otherwise {@link Optional#empty()}.
     */
    Optional<BankCardEntity> findByUserEntity_KeycloakIdAndIsDefaultTrue(String keycloakId);

    /**
     * BUG-038: returns every card belonging to a given user, used by
     * {@code setDefault} to clear the existing default flag before flipping the new one.
     *
     * @param keycloakId the authenticated user's Keycloak subject identifier.
     * @return all cards owned by that user (possibly empty).
     */
    List<BankCardEntity> findAllByUserEntity_KeycloakId(String keycloakId);
}
