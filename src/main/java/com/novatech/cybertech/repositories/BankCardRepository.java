package com.novatech.cybertech.repositories;

import java.util.Optional;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
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

    /**
     * The card of a user, with the user fetched (its Stripe Customer id is needed to charge it).
     * Looked up by id so callers holding a detached/lazy {@code UserEntity} proxy can use it.
     */
    @Query("select b from BankCardEntity b join fetch b.userEntity u where u.id = :userId")
    Optional<BankCardEntity> findWithUserByUserId(@Param("userId") Long userId);
}
