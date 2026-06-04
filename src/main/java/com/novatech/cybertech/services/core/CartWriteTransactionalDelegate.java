package com.novatech.cybertech.services.core;

import com.novatech.cybertech.dto.request.cart.CartCreateRequestDto;
import com.novatech.cybertech.dto.response.cart.CartResponseDto;

/**
 * Transactional inner half of the cart-add concurrency design (BUG-160).
 *
 * <h2>Why this is a separate bean</h2>
 * Adding items to a cart is a read-modify-write (RMW): {@code load cart → mutate quantities →
 * save}. Two concurrent {@code POST /cart/add} for the <em>same</em> user can interleave and lose
 * an increment (classic lost-update):
 * <pre>{@code
 * Thread A: read qty=1 ─┐
 * Thread B: read qty=1 ─┤  both read before either writes
 * Thread A: write qty=2 │
 * Thread B: write qty=2 ┘  ← A's +1 is lost, should be 3
 * }</pre>
 *
 * We serialise that RMW with a per-user <b>Redis distributed lock</b> (a single-node Redlock) held
 * by {@code CartServiceImp.addItemsToCart}. But a Redis lock is <em>not</em> the same object as the
 * JPA transaction — nothing intrinsically orders "release the lock" against "commit the tx". If the
 * mutation ran under a plain method-level {@code @Transactional} on the lock-holder, the order would
 * be:
 * <pre>{@code
 * acquire-lock → [tx open] → mutate → release-lock(finally) → [tx commit on method return]
 *                                      ▲ lock freed BEFORE the commit
 * }</pre>
 * A waiting thread could then grab the lock and read the cart row <em>before</em> the first thread's
 * changes were committed — and the lost-update is back, despite the lock.
 *
 * <p>The fix is the <b>two-method split</b>: the lock is held by a NON-transactional outer method
 * ({@code CartServiceImp.addItemsToCart}), and the mutation lives here, in a {@code @Transactional}
 * method on a <em>different</em> Spring bean. Because Spring's transaction advice is applied by a
 * proxy, it only triggers on a call that crosses the bean boundary — a self-invocation inside the
 * same class would be ignored. Routing through this separate bean makes the lifecycle:
 * <pre>{@code
 * acquire-lock → delegate call → tx-begin → mutate → tx-commit (on return) → release-lock
 *                                                     ▲ commit happens INSIDE the locked region
 * }</pre>
 * so when the lock is released the data is already durable, and the next thread reads fresh state.
 *
 * <p>This mirrors the existing {@code ShipOrderTransactionalDelegate} idiom: a {@code REQUIRES_NEW}
 * helper bean used precisely so a commit boundary lands inside an outer orchestration call.
 *
 * @see com.novatech.cybertech.services.implementation.CartCacheHelperImp CartCacheHelperImp — the
 *      Redis lock itself (atomic {@code SET NX EX}, token-fenced Lua unlock, raw-byte serialisation).
 */
public interface CartWriteTransactionalDelegate {

    /**
     * Performs the add-items read-modify-write inside its own committed transaction.
     *
     * <p><b>Precondition:</b> the caller MUST already hold the per-user cart lock. This method does
     * not acquire or release it — that is the outer method's job — it only guarantees that, by the
     * time it returns normally, its DB changes are committed (so the lock can be safely released
     * afterwards without exposing pre-commit state to the next waiter).
     *
     * @param dto        items to add; each quantity already validated {@code >= 1} by the caller.
     * @param keycloakId Keycloak subject of the cart owner.
     * @return the updated, persisted-and-cached cart DTO.
     */
    CartResponseDto addItemsWithinTransaction(CartCreateRequestDto dto, String keycloakId);
}
