package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.dto.request.cart.CartCreateRequestDto;
import com.novatech.cybertech.dto.request.cart.CartItemAddRequestDto;
import com.novatech.cybertech.dto.request.cart.CartItemRemoveRequestDto;
import com.novatech.cybertech.dto.request.cart.CartUpdateRequestDto;
import com.novatech.cybertech.dto.response.cart.CartResponseDto;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.exceptions.*;
import com.novatech.cybertech.mappers.entity.CartMapper;
import com.novatech.cybertech.repositories.UserRepository;
import com.novatech.cybertech.services.core.CartCacheHelper;
import com.novatech.cybertech.services.core.CartService;
import com.novatech.cybertech.services.core.CartWriteTransactionalDelegate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartServiceImp implements CartService {

    private final CartMapper cartMapper;
    private final UserRepository userRepository;
    private final CartCacheHelper cartCacheHelper;

    /**
     * The transactional inner half of the cart-add design. Held as a separate Spring bean
     * (not an inlined private method) so Spring's transaction proxy actually applies when we cross
     * the bean boundary — the basis of the commit-before-unlock guarantee. See
     * {@link CartWriteTransactionalDelegate} for the full rationale.
     */
    private final CartWriteTransactionalDelegate cartWriteTransactionalDelegate;

    /**
     * Maximum time {@link #addItemsToCart(CartCreateRequestDto, String)} will
     * wait to acquire the per-user lock before giving up and asking the caller
     * to retry. The lock's own hold time isn't capped by a fixed TTL — Redisson's
     * watchdog (see {@link CartCacheHelperImp}) keeps extending it for as long as
     * the holder is genuinely still working, so this budget is purely about how
     * long a caller is willing to queue behind a concurrent write.
     */
    private static final long CART_ADD_LOCK_WAIT_MS = 4_000L;

    /**
     * Bounded wait for the cache-rebuild lock on a {@link #getCart} cache miss. Short relative
     * to {@link #CART_ADD_LOCK_WAIT_MS} — a rebuild is a single DB read, not a read-modify-write,
     * so a concurrent rebuilder should clear quickly. Reused via
     * {@link CartCacheHelper#acquireLockBlocking} instead of a fixed sleep so a caller queues
     * only as long as actually needed rather than a worst-case guess, and — combined with the
     * double-check once the lock is acquired, see {@link #getCart} — a losing reader reuses the
     * winner's freshly-cached result instead of either returning a wrong empty cart or
     * redundantly re-reading the DB.
     */
    private static final long CART_REBUILD_LOCK_WAIT_MS = 1_500L;

    /**
     * Add items to the authenticated user's cart, serialising concurrent writes for the
     * same user with a per-user <b>Redis distributed lock</b> ({@link CartCacheHelper}, backed by
     * Redisson's {@code RLock}).
     *
     * <h2>The race being defended against</h2>
     * Adding items is a read-modify-write: {@code load cart → merge quantities → save}. Two
     * concurrent {@code POST /cart/add} for the same user can interleave so the second save
     * overwrites the first (lost-update):
     * <pre>{@code
     * Thread A: read qty=1 ─┐
     * Thread B: read qty=1 ─┤  both read before either writes
     * Thread A: write qty=2 │
     * Thread B: write qty=2 ┘  ← A's +1 is lost; the correct result is 3
     * }</pre>
     *
     * <h2>The two surviving layers (and the one removed)</h2>
     * <ol>
     *   <li><b>Layer 1 — Redis lock.</b> {@link CartCacheHelper#acquireLockBlocking} waits up to
     *       {@link #CART_ADD_LOCK_WAIT_MS} to acquire the per-user lock; {@link CartCacheHelperImp}
     *       delegates the acquire/release/TTL-extension mechanics to Redisson. This is the
     *       application-level mutual exclusion, and it works across pods (a JVM {@code synchronized}
     *       would not).</li>
     *   <li><b>Layer 2 — commit-before-unlock, via the two-method split.</b> A Redis lock is a
     *       different object from the JPA transaction; nothing intrinsically orders "release lock"
     *       against "commit tx". If the mutation ran under a plain {@code @Transactional} on THIS
     *       lock-holding method, the {@code finally} that releases the lock would run before the
     *       proxy commits — and a waiter could read the pre-commit cart, reviving the lost-update.
     *       So the mutation lives on a SEPARATE bean,
     *       {@link CartWriteTransactionalDelegate#addItemsWithinTransaction}: crossing the bean
     *       boundary makes Spring's transaction proxy fire, and the delegate's {@code @Transactional}
     *       method commits when it returns — i.e. INSIDE the locked region. Lifecycle:
     *       {@code acquire-lock → delegate (tx-begin → mutate → tx-commit) → release-lock}.
     *       Swapping the lock mechanics for Redisson doesn't change this: no lock library can know
     *       where our transaction boundary is, so the two-bean split stays regardless.</li>
     * </ol>
     *
     * <h3>Why the old DB pessimistic lock (layer 3) was removed</h3>
     * The previous design ALSO took a {@code SELECT ... FOR UPDATE} on the cart row
     * ({@code findByOwnerKeycloakIdForUpdate}) plus a {@code UNIQUE(userId)} + one-shot
     * {@code DataIntegrityViolationException} retry, as a "bulletproof" DB-level second line of
     * defence. For a single MySQL that is redundant: the Redis lock already serialises the RMW per
     * user, including the first-insert case — when a brand-new user's two concurrent adds contend,
     * the loser simply waits for the winner to commit-and-release, then reads the now-existing cart
     * and merges into it. Carrying two independent locking mechanisms for one invariant was the
     * over-engineering we set out to remove, so the FOR UPDATE query and the DIVE retry are gone.
     * The {@code UNIQUE(userId)} constraint is intentionally kept as a cheap, declarative
     * data-integrity invariant ("one cart per user"), but it is no longer load-bearing for
     * concurrency. The deliberate residual risk: if Redis were unavailable the lock would silently
     * become a no-op and a concurrent first-insert could surface a raw DIVE — acceptable for this
     * project, where Redis is a hard dependency of the cart path anyway.
     *
     * <p>Quantity validation (non-null, {@code >= 1}) is not repeated here: it lives on
     * {@link CartItemAddRequestDto#getQuantity()} (bean validation) and is already enforced by
     * {@code @Valid} on the sole caller, {@code CartManagementController#addToCart}, before this
     * method is ever reached.
     *
     * @param cartCreateRequestDto items to add; each quantity must be {@code >= 1}.
     * @param keycloakId           Keycloak subject of the caller.
     * @return the updated cart DTO.
     * @throws IllegalStateException     when the per-user lock cannot be acquired within the budget.
     * @throws UserNotFoundException     when no user matches {@code keycloakId}.
     * @throws ProductNotFoundException  when a requested product UUID has no product.
     * @throws NotEnoughStockException   when the resulting total exceeds available stock.
     */
    @Override
    public CartResponseDto addItemsToCart(final CartCreateRequestDto cartCreateRequestDto, final String keycloakId) {

        // Layer 1 — acquire the per-user Redis lock (bounded wait).
        final boolean acquired = cartCacheHelper.acquireLockBlocking(keycloakId, CART_ADD_LOCK_WAIT_MS);
        if (!acquired) {
            // Couldn't get the lock in time — surface a retryable error rather than racing.
            log.warn("Could not acquire cart lock for user {} within {}ms — aborting addItemsToCart", keycloakId, CART_ADD_LOCK_WAIT_MS);
            throw new IllegalStateException("Cart is temporarily locked by a concurrent operation, please retry.");
        }

        try {
            // Layer 2 — delegate the read-modify-write to a SEPARATE @Transactional bean so the
            // commit lands INSIDE this locked region (commit-before-unlock). Crossing the bean
            // boundary is what makes Spring's transaction proxy fire — a self-invoked private method
            // would silently run without a transaction and reopen the lost-update race.
            final CartResponseDto result = cartWriteTransactionalDelegate.addItemsWithinTransaction(cartCreateRequestDto, keycloakId);
            // Cache write happens HERE, after the delegate call returned — i.e. after its
            // transaction actually committed — so Redis can never end up holding a cart state that
            // got rolled back.
            cartCacheHelper.putWithJitter(keycloakId, result);
            return result;
        } finally {
            // Always release the lock — Redisson tracks ownership per-thread, so this is a no-op
            // if the lock already expired and was reacquired by someone else.
            cartCacheHelper.releaseLock(keycloakId);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public CartResponseDto getCart(final String keycloakId) {

        // 1) Try cache first — sliding TTL refresh on hit
        final CartResponseDto cached = cartCacheHelper.getRaw(keycloakId);
        if (cached != null) {
            cartCacheHelper.refreshTtlWithJitter(keycloakId);
            return cached;
        }

        // 2) Cache miss → block briefly for the rebuild lock instead of racing past it with a
        // single non-blocking probe. Whoever is already rebuilding is doing a single DB read, so
        // it usually finishes in a handful of ms — a short bounded wait resolves the race
        // correctly far more often than a one-shot probe, without making every reader wait a
        // fixed worst-case delay (see CART_REBUILD_LOCK_WAIT_MS).
        final boolean acquired = cartCacheHelper.acquireLockBlocking(keycloakId, CART_REBUILD_LOCK_WAIT_MS);

        if (!acquired) {
            // Waited the full budget and still couldn't get the lock — genuinely unusual (mirrors
            // addItemsToCart's own failure mode). Best-effort final read.
            final CartResponseDto retry = cartCacheHelper.getRaw(keycloakId);
            return retry != null ? retry : new CartResponseDto();
        }

        try {
            // 3) Double-check: whoever held the lock before us may have already rebuilt and
            // populated the cache while we were queued — reuse their result instead of hitting
            // the DB a second time for nothing.
            final CartResponseDto rebuiltWhileWaiting = cartCacheHelper.getRaw(keycloakId);
            if (rebuiltWhileWaiting != null) {
                return rebuiltWhileWaiting;
            }

            // 4) Still nothing — we're genuinely the one rebuilding. Load from DB.
            final UserEntity user = userRepository.findByKeycloakId(keycloakId).orElseThrow(() -> new UserNotFoundException("User not found"));

            final CartResponseDto dto = user.getCartEntity() == null
                    ? new CartResponseDto()
                    : cartMapper.mapFromEntityToResponseDto(user.getCartEntity());

            // 5) Populate cache with jitter TTL
            cartCacheHelper.putWithJitter(keycloakId, dto);

            return dto;

        } finally {
            // 6) Always release the lock
            log.info("Releasing rebuild lock for user {}", keycloakId);
            cartCacheHelper.releaseLock(keycloakId);
        }
    }

    @Override
    public CartResponseDto removeItemFromCart(final UUID productUuid, final String keycloakId) {
        // See addItemsToCart: the mutation runs in cartWriteTransactionalDelegate's own committed
        // transaction; the cache is only written once that call has returned.
        final CartResponseDto result = cartWriteTransactionalDelegate.removeItemWithinTransaction(productUuid, keycloakId);
        cartCacheHelper.putWithJitter(keycloakId, result);
        return result;
    }

    @Override
    public CartResponseDto decreaseQuantity(final CartItemRemoveRequestDto cartItemRemoveRequestDto, final String keycloakId) {
        final CartResponseDto result = cartWriteTransactionalDelegate.decreaseQuantityWithinTransaction(cartItemRemoveRequestDto, keycloakId);
        cartCacheHelper.putWithJitter(keycloakId, result);
        return result;
    }

    @Override
    public void clearCart(final String keycloakId) {
        final CartResponseDto result = cartWriteTransactionalDelegate.clearCartWithinTransaction(keycloakId);
        // null means the user had no cart / an already-empty cart — nothing to persist or cache.
        if (result != null) {
            cartCacheHelper.putWithJitter(keycloakId, result);
        }
    }

    /**
     * New, correctly-typed, ownership-checked cart update.
     * <p>
     * Loads the cart by UUID, asserts the caller owns it, replaces its items
     * with the incoming list (existing lines are cleared and re-created from
     * {@link CartUpdateRequestDto#getCartItemAddRequestDtos()}), persists, and
     * refreshes the cache. Using a dedicated DTO rather than the historical
     * {@link CartItemRemoveRequestDto} makes the intent explicit ("replace my
     * cart items with this list").
     *
     * @param cartUuid   target cart UUID.
     * @param dto        new items payload.
     * @param keycloakId caller identity.
     * @return the updated cart DTO.
     * @throws CartNotFoundException           when no cart matches {@code cartUuid}.
     * @throws UnauthorizedCartAccessException when the caller does not own the cart.
     * @throws ProductNotFoundException        when any item points at an unknown product.
     */
    @Override
    public CartResponseDto updateCart(final UUID cartUuid, final CartUpdateRequestDto dto, final String keycloakId) {
        final CartResponseDto result = cartWriteTransactionalDelegate.updateCartWithinTransaction(cartUuid, dto, keycloakId);
        cartCacheHelper.putWithJitter(keycloakId, result);
        return result;
    }
}
