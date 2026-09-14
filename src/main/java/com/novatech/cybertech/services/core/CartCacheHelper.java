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
 *       {@link #acquireLockBlocking} (used by the write paths to serialize the
 *       read-modify-write of {@code addItemsToCart} across competing threads or
 *       pods). Backed by Redisson's {@code RLock}, which tracks lock ownership
 *       per-thread internally — no token needs to travel between acquire and
 *       release.</li>
 * </ul>
 */
public interface CartCacheHelper {

    /**
     * Non-blocking lock acquisition. Returns {@code true} on success, {@code false}
     * if another worker already holds the lock.
     */
    boolean acquireLock(String userId);

    /**
     * Blocking acquisition with bounded wait. Waits up to {@code timeoutMillis} for
     * the lock to become available. Returns {@code true} on success or {@code false}
     * on timeout. The caller is responsible for {@link #releaseLock(String) releasing}
     * it, from the same thread that acquired it.
     *
     * @param userId the user (Keycloak id) the lock is keyed on
     * @param timeoutMillis maximum total wait before giving up
     */
    boolean acquireLockBlocking(String userId, long timeoutMillis);

    /**
     * Releases a lock previously taken via {@link #acquireLock} or
     * {@link #acquireLockBlocking}, from the same thread that acquired it. Safe to
     * call when the lock isn't (or is no longer) held by the current thread — a no-op
     * in that case rather than throwing.
     */
    void releaseLock(String userId);

    void refreshTtlWithJitter(String userId);

    void putWithJitter(String userId, CartResponseDto cart);

    CartResponseDto getRaw(String userId);
}
