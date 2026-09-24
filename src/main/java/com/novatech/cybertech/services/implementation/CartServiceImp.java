package com.novatech.cybertech.services.implementation;

import com.github.f4b6a3.uuid.UuidCreator;
import com.novatech.cybertech.dto.request.cart.CartCreateRequestDto;
import com.novatech.cybertech.dto.request.cart.CartItemAddRequestDto;
import com.novatech.cybertech.dto.request.cart.CartItemRemoveRequestDto;
import com.novatech.cybertech.dto.request.cart.CartUpdateRequestDto;
import com.novatech.cybertech.dto.response.cart.CartResponseDto;
import com.novatech.cybertech.entities.CartEntity;
import com.novatech.cybertech.entities.CartItemEntity;
import com.novatech.cybertech.entities.ProductEntity;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.exceptions.*;
import com.novatech.cybertech.mappers.entity.CartMapper;
import com.novatech.cybertech.repositories.CartRepository;
import com.novatech.cybertech.repositories.ProductRepository;
import com.novatech.cybertech.repositories.UserRepository;
import com.novatech.cybertech.services.core.CartCacheHelper;
import com.novatech.cybertech.services.core.CartService;
import com.novatech.cybertech.services.core.CartWriteTransactionalDelegate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartServiceImp implements CartService {

    private final CartMapper cartMapper;
    private final UserRepository userRepository;
    private final CartRepository cartRepository;
    private final CartCacheHelper cartCacheHelper;
    private final ProductRepository productRepository;

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
            return cartWriteTransactionalDelegate.addItemsWithinTransaction(cartCreateRequestDto, keycloakId);
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
    @Transactional
    public CartResponseDto removeItemFromCart(final UUID productUuid, final String keycloakId) {

        final UserEntity user = userRepository.findByKeycloakId(keycloakId).orElseThrow(() -> new UserNotFoundException("User not found"));
        final CartEntity cart = user.getCartEntity();

        if (cart != null && cart.getCartItems() != null) {
            boolean removed = cart.getCartItems().removeIf(item -> item.getProductEntity().getUuid().equals(productUuid));

            if (removed) {
                CartEntity savedCart = cartRepository.save(cart);
                CartResponseDto cartResponseDto = cartMapper.mapFromEntityToResponseDto(savedCart);

                cartCacheHelper.putWithJitter(keycloakId, cartResponseDto);

                return cartResponseDto;
            }
        }
        throw new CannotRemoveItemFromEmptyCartException("Cannot remove item already absent from cart.");
    }

    @Override
    @Transactional
    public CartResponseDto decreaseQuantity(final CartItemRemoveRequestDto cartItemRemoveRequestDto, final String keycloakId) {

        final UserEntity user = userRepository.findByKeycloakId(keycloakId).orElseThrow(() -> new UserNotFoundException("User not found"));
        final CartEntity cart = user.getCartEntity();

        if (cart == null || cart.getCartItems() == null || cart.getCartItems().isEmpty()) {
            throw new CartIsEmptyException("Cannot decrease quantity from an empty cart.");
        }

        final CartItemEntity cartItemToDecrease = cart.getCartItems().stream()
                .filter(item -> item.getProductEntity().getUuid().equals(cartItemRemoveRequestDto.getProductUuid()))
                .findFirst()
                .orElseThrow(() -> new CartItemNotFoundException("Product doesn't exist in cart"));

        final Integer updateResult = cartItemToDecrease.decreaseQuantity(cartItemRemoveRequestDto.getQuantity());

        // Le service gère la logique de la collection (suppression de l'item)

        if (updateResult <= 0) {
            cart.getCartItems().remove(cartItemToDecrease);
        }

        CartEntity savedCart = cartRepository.save(cart);
        CartResponseDto cartResponseDto = cartMapper.mapFromEntityToResponseDto(savedCart);
        cartCacheHelper.putWithJitter(keycloakId, cartResponseDto);

        return cartResponseDto;
    }

    @Override
    @Transactional
    public void clearCart(final String keycloakId) {

        final UserEntity user = userRepository.findByKeycloakId(keycloakId).orElseThrow(() -> new UserNotFoundException("User not found"));
        final CartEntity cart = user.getCartEntity();

        if (cart != null && cart.getCartItems() != null) {
            cart.getCartItems().clear(); // Vide la liste
            final CartEntity savedCart = cartRepository.save(cart);   // Sauvegarde l'état vide
            // Replace the helper-keyed cache entry with the cleared cart so the
            // next read sees the new empty state without hitting the DB.
            cartCacheHelper.putWithJitter(keycloakId, cartMapper.mapFromEntityToResponseDto(savedCart));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Collection<CartResponseDto> getAll() {
        return cartMapper.mapFromEntityToResponseDto(cartRepository.findAll());
    }

    @Override
    @Transactional(readOnly = true)
    public CartResponseDto getByUUID(UUID uuid) {
        return cartMapper.mapFromEntityToResponseDto(cartRepository.findByUuid(uuid).orElseThrow(() -> new CartNotFoundException("No cart with the UUID : " + uuid + " found")));
    }

    /**
     * Ownership-checked read-by-UUID.
     * <p>
     * Loads the cart, asserts the caller's Keycloak subject matches
     * {@code cart.userEntity.keycloakId}, then maps. The single-arg
     * {@link #getByUUID(UUID)} stays in place because several places still rely
     * on the {@link com.novatech.cybertech.services.core.CrudBaseService}
     * contract — adding an overload rather than changing the signature keeps the
     * blast radius of the fix scoped to the cart cluster.
     *
     * @param cartUuid   cart to read.
     * @param keycloakId Keycloak subject of the caller.
     * @return the cart DTO owned by the caller.
     * @throws CartNotFoundException           when no cart with that UUID exists.
     * @throws UnauthorizedCartAccessException when the cart's owner is not the caller.
     */
    @Override
    @Transactional(readOnly = true)
    public CartResponseDto getByUUID(final UUID cartUuid, final String keycloakId) {
        final CartEntity cart = cartRepository.findByUuid(cartUuid)
                .orElseThrow(() -> new CartNotFoundException("No cart with the UUID : " + cartUuid + " found"));
        assertCallerOwnsCart(cart, cartUuid, keycloakId);
        return cartMapper.mapFromEntityToResponseDto(cart);
    }

    @Override
    @Transactional(readOnly = true)
    public Collection<CartResponseDto> getByUUIDs(Collection<UUID> uuids) {
        return cartMapper.mapFromEntityToResponseDto(cartRepository.findAllByUuidIn(uuids));
    }

    @Override
    @Transactional
    public CartResponseDto create(CartCreateRequestDto cartCreateRequestDto) {
        return cartMapper.mapFromEntityToResponseDto(cartRepository.save(cartMapper.mapFromCreationRequestToEntity(cartCreateRequestDto)));
    }

    @Override
    @Transactional
    public CartResponseDto update(final CartItemRemoveRequestDto cartCreateRequestDto) {
        return cartMapper.mapFromEntityToResponseDto(cartRepository.save(cartMapper.mapFromUpdateRequestToEntity(cartCreateRequestDto)));
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
     * <p>
     * Kept as a NEW method instead of modifying
     * {@link #update(CartItemRemoveRequestDto)} so the
     * {@link com.novatech.cybertech.services.core.CrudBaseService}
     * generics contract (and every caller elsewhere) stays untouched.
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
    @Transactional
    public CartResponseDto updateCart(final UUID cartUuid, final CartUpdateRequestDto dto, final String keycloakId) {
        final CartEntity cart = cartRepository.findByUuid(cartUuid)
                .orElseThrow(() -> new CartNotFoundException("No cart with the UUID : " + cartUuid + " found"));
        assertCallerOwnsCart(cart, cartUuid, keycloakId);

        final List<CartItemAddRequestDto> items = dto.getCartItemAddRequestDtos();

        // Validate every line (product existence + stock availability) BEFORE
        // mutating the cart. Without this fail-fast pass, updateCart would clear the
        // existing items and re-add them one by one; if line N had insufficient stock
        // we would have already wiped the cart and partially rebuilt it. Loading the
        // products via findAllByUuidIn keeps this on a single DB roundtrip — the same
        // pattern doAddItemsToCart already uses — so no extra queries are introduced.
        final Map<UUID, ProductEntity> productMap;
        if (items != null && !items.isEmpty()) {
            final List<UUID> productUuids = items.stream()
                    .map(CartItemAddRequestDto::getProductUuid)
                    .collect(Collectors.toList());
            productMap = productRepository.findAllByUuidIn(productUuids).stream()
                    .collect(Collectors.toMap(ProductEntity::getUuid, p -> p));

            for (final CartItemAddRequestDto line : items) {
                final ProductEntity product = productMap.get(line.getProductUuid());
                if (product == null) {
                    throw new ProductNotFoundException("No product with the UUID : " + line.getProductUuid() + " found");
                }
                CartStockValidator.validateStockAvailability(product, line.getQuantity());
            }
        } else {
            productMap = Map.of();
        }

        if (cart.getCartItems() != null) {
            cart.getCartItems().clear();
        }

        if (items != null && !items.isEmpty()) {
            for (final CartItemAddRequestDto line : items) {
                final ProductEntity product = productMap.get(line.getProductUuid());
                final CartItemEntity newItem = CartItemEntity.builder()
                        .quantity(line.getQuantity())
                        .productEntity(product)
                        .cart(cart)
                        .uuid(UuidCreator.getTimeOrderedEpoch())
                        .build();
                cart.getCartItems().add(newItem);
            }
        }

        final CartEntity saved = cartRepository.save(cart);
        final CartResponseDto resp = cartMapper.mapFromEntityToResponseDto(saved);
        cartCacheHelper.putWithJitter(keycloakId, resp);
        return resp;
    }

    @Override
    @Transactional
    public void deleteByUUID(UUID uuid) {
        cartRepository.deleteByUuid(uuid);
    }

    /**
     * Ownership-checked delete-by-UUID.
     * <p>
     * Loads the cart, asserts ownership, then delegates to the repository. The
     * single-arg {@link #deleteByUUID(UUID)} is kept for the
     * {@link com.novatech.cybertech.services.core.CrudBaseService} contract.
     *
     * @param cartUuid   cart to delete.
     * @param keycloakId caller identity.
     * @throws CartNotFoundException           when no cart with that UUID exists.
     * @throws UnauthorizedCartAccessException when the caller does not own the cart.
     */
    @Override
    @Transactional
    public void deleteByUUID(final UUID cartUuid, final String keycloakId) {
        final CartEntity cart = cartRepository.findByUuid(cartUuid)
                .orElseThrow(() -> new CartNotFoundException("No cart with the UUID : " + cartUuid + " found"));
        assertCallerOwnsCart(cart, cartUuid, keycloakId);

        // Break the inverse-side reference before delegating the delete. Without this,
        // the User entity (now managed in the persistence context after the ownership
        // check navigated cart.userEntity) still holds a `cartEntity` reference. With
        // cascade=CascadeType.ALL on the User → Cart inverse mapping, Hibernate may
        // re-cascade-persist the soon-to-be-deleted cart back at flush/commit time,
        // leaving the row in place and silently defeating the delete.
        final UserEntity owner = cart.getUserEntity();
        if (owner != null) {
            owner.setCartEntity(null);
        }

        cartRepository.deleteByUuid(cartUuid);
    }

    @Override
    @Transactional
    public void deleteByUUIDs(Collection<UUID> uuids) {
        cartRepository.deleteAllByUuidIn(uuids);
    }

    /**
     * Central ownership guard.
     * <p>
     * Throws {@link UnauthorizedCartAccessException} when the caller's
     * Keycloak id does not match the cart's owner. Extracted so the three
     * ownership-checked entrypoints ({@link #getByUUID(UUID, String)},
     * {@link #deleteByUUID(UUID, String)}, {@link #updateCart(UUID, CartUpdateRequestDto, String)})
     * share a single implementation and error message shape.
     *
     * @param cart       cart entity loaded from the repository.
     * @param cartUuid   the UUID that identified the cart (for error logging).
     * @param keycloakId caller identity.
     * @throws UnauthorizedCartAccessException when the caller does not own the cart.
     */
    private void assertCallerOwnsCart(final CartEntity cart, final UUID cartUuid, final String keycloakId) {
        final UserEntity owner = cart.getUserEntity();
        if (owner == null || owner.getKeycloakId() == null || !owner.getKeycloakId().equals(keycloakId)) {
            log.warn("Unauthorized cart access attempt: caller {} on cart {}", keycloakId, cartUuid);
            throw new UnauthorizedCartAccessException("Caller does not own cart " + cartUuid);
        }
    }
}
