package com.novatech.cybertech.repositories;

import com.novatech.cybertech.entities.CartEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Cart persistence.
 *
 * <p>History: this repository used to expose a {@code findByOwnerKeycloakIdForUpdate}
 * ({@code SELECT ... FOR UPDATE}) pessimistic-lock lookup as a DB-level second line of defence on
 * top of the per-user Redis lock. That "layer 3" was removed once the Redis lock became the single
 * serialisation point for the cart-add read-modify-write — see
 * {@code CartServiceImp.addItemsToCart} for the full rationale. The cart is now resolved by lazy
 * navigation from the user, so no custom locking query lives here anymore.
 */
@Repository
public interface CartRepository extends CrudBaseRepository<CartEntity, Long> {

    /**
     * Backs the admin {@code CartServiceImp#getAll} listing with a single round trip instead of
     * the N (items) + N*M (products) lazy-load chain {@code CartMapper} would otherwise trigger
     * walking every cart's {@code cartItems} and each item's {@code productEntity}.
     */
    @Query("""
            SELECT DISTINCT c
            FROM   CartEntity c
            LEFT   JOIN FETCH c.userEntity
            LEFT   JOIN FETCH c.cartItems ci
            LEFT   JOIN FETCH ci.productEntity
            """)
    List<CartEntity> findAllWithItemsAndProducts();
}
