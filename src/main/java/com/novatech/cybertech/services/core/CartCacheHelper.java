package com.novatech.cybertech.services.core;

import com.novatech.cybertech.dto.response.cart.CartResponseDto;

/**
 * Cart cache + cart-write distributed-lock contract.
 * <p>
 * Two responsibilities live here on purpose:
 * <ul>
 *   <li><b>Read-through cache</b>: {@link #getRaw}, {@link #putWithJitter},
 *       {@link #refreshTtlWithJitter} expose the {@code cart::&lt;userId&gt;} key.</li>
 *   <li><b>Per-user serialization</b>: {@link #acquireLock}/{@link #releaseLock}
 *       (non-blocking, used on the read-rebuild path) and
 *       {@link #acquireLockBlocking} (BUG-160 fix — used by the write paths to
 *       serialize the read-modify-write of {@code addItemsToCart} across
 *       competing threads or pods).</li>
 * </ul>
 */
public interface CartCacheHelper {

    /**
     * Non-blocking lock acquisition. Returns the lock token on success, {@code null}
     * if another worker already holds the lock.
     */
    String acquireLock(String userId);

    /**
     * BUG-160 — Blocking acquisition with bounded wait. Spins with a small
     * back-off until the lock is acquired or {@code timeoutMillis} elapses.
     * Returns the lock token on success or {@code null} on timeout. The caller
     * is responsible for {@link #releaseLock(String, String) releasing} it.
     *
     * @param userId the user (Keycloak id) the lock is keyed on
     * @param timeoutMillis maximum total wait before giving up
     */
    String acquireLockBlocking(String userId, long timeoutMillis);

    /**
     * Releases a lock previously taken via {@link #acquireLock} or
     * {@link #acquireLockBlocking}. Safe to call with a stale token — the
     * Lua script makes the release token-checked (CAS) so we never delete
     * someone else's lock.
     */
    void releaseLock(String userId, String token);

    void refreshTtlWithJitter(String userId);

    void putWithJitter(String userId, CartResponseDto cart);

    CartResponseDto getRaw(String userId);
}
