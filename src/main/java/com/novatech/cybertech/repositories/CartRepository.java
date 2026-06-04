package com.novatech.cybertech.repositories;

import com.novatech.cybertech.entities.CartEntity;
import org.springframework.stereotype.Repository;

/**
 * Cart persistence.
 *
 * <p>BUG-160 history: this repository used to expose a {@code findByOwnerKeycloakIdForUpdate}
 * ({@code SELECT ... FOR UPDATE}) pessimistic-lock lookup as a DB-level second line of defence on
 * top of the per-user Redis lock. That "layer 3" was removed once the Redis lock became the single
 * serialisation point for the cart-add read-modify-write — see
 * {@code CartServiceImp.addItemsToCart} for the full rationale. The cart is now resolved by lazy
 * navigation from the user, so no custom locking query lives here anymore.
 */
@Repository
public interface CartRepository extends CrudBaseRepository<CartEntity, Long> {
}
