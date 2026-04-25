package com.novatech.cybertech.repositories;

import com.novatech.cybertech.entities.CartEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CartRepository extends CrudBaseRepository<CartEntity, Long> {

    /**
     * BUG-160 — Pessimistic-write lookup of a cart by its owner's Keycloak id.
     * <p>
     * Used by {@code CartServiceImp.addItemsToCart} as a second line of defence
     * (in addition to the per-user Redis lock) to serialise concurrent
     * read-modify-write of the same user's cart at the database level: the
     * {@code SELECT ... FOR UPDATE} blocks any other transaction that tries to
     * lock or modify the same row until the current transaction commits, so the
     * second concurrent /cart/add cannot read a stale snapshot of the cart.
     * <p>
     * Returns {@link Optional#empty()} when the user has no cart yet — the
     * service then creates one inside the same transaction.
     *
     * @param keycloakId Keycloak subject of the cart owner.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CartEntity c WHERE c.userEntity.keycloakId = :keycloakId")
    Optional<CartEntity> findByOwnerKeycloakIdForUpdate(@Param("keycloakId") String keycloakId);
}