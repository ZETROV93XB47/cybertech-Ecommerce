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
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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
     * BUG-160 — Built lazily from {@link PlatformTransactionManager} so the transaction
     * for {@code addItemsToCart} can begin AFTER the Redis lock is acquired and commit
     * BEFORE it is released. Field-injected (rather than added to {@code @RequiredArgsConstructor})
     * to avoid breaking the existing {@code @InjectMocks}-based unit tests, which neither
     * mock nor exercise this path's transactional commit boundary.
     */
    @Autowired(required = false)
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;

    @PostConstruct
    void initTransactionTemplate() {
        if (transactionManager != null) {
            this.transactionTemplate = new TransactionTemplate(transactionManager);
            log.info("BUG-160 — CartServiceImp initialised with TransactionTemplate (manager={})", transactionManager.getClass().getSimpleName());
        } else {
            log.warn("BUG-160 — CartServiceImp has no PlatformTransactionManager; addItemsToCart will run without explicit programmatic tx (unit-test fallback)");
        }
    }


    /**
     * Maximum time {@link #addItemsToCart(CartCreateRequestDto, String)} will
     * wait for the per-user lock before giving up. Kept just under the 5s TTL
     * on the lock key itself (see {@link CartCacheHelperImp#LOCK_DURATION_IN_SECONDS})
     * so a stuck worker releases its slot well before we stop retrying.
     */
    private static final long CART_ADD_LOCK_WAIT_MS = 4_000L;

    /**
     * BUG-160 — Add items to the authenticated user's cart inside a
     * per-user distributed Redis lock <em>and</em> with a pessimistic DB row
     * lock around the read-modify-write of the cart row.
     * <p>
     * The Redis lock spans the full <em>load cart → mutate → save → cache-write</em>
     * section. The transaction is opened <em>inside</em> the lock via
     * {@link TransactionTemplate} so the commit is guaranteed to happen before
     * the lock is released — without that, two concurrent {@code POST /cart/add}
     * requests for the same user could both read the same cart row, each mutate
     * their in-memory copy, and have the second save silently overwrite the first
     * (classic lost-update). With the lock+pessimistic-FOR-UPDATE in place only
     * one write path runs at a time per user, so the final quantity is the sum
     * of all concurrent additions.
     * <p>
     * Validation that throws (negative quantity, missing product, not enough
     * stock) is done inside the lock too, but it was intentionally kept
     * <em>after</em> the input-level null/negative guard so obviously-bogus
     * payloads fail fast without taking the lock.
     *
     * @param cartCreateRequestDto items to add; each quantity must be {@code >= 1}.
     * @param keycloakId           Keycloak subject of the caller.
     * @return the updated cart DTO.
     * @throws NegativeQuantityException when any requested quantity is null or below 1.
     * @throws UserNotFoundException     when no user matches {@code keycloakId}.
     * @throws ProductNotFoundException  when a requested product UUID has no product.
     * @throws NotEnoughStockException   when the resulting total exceeds available stock.
     */
    @Override
    public CartResponseDto addItemsToCart(final CartCreateRequestDto cartCreateRequestDto, final String keycloakId) {

        log.info("cart request dto : {}", cartCreateRequestDto);

        // Fast-fail before taking the lock — saves contention on obviously-bogus input.
        cartCreateRequestDto.getCartItemAddRequestDtos().forEach(item -> {
            if (item.getQuantity() == null || item.getQuantity() < 1) {
                throw new NegativeQuantityException("Quantity must be >= 1 (got " + item.getQuantity() + ") for product " + item.getProductUuid());
            }
        });

        final String lockToken = cartCacheHelper.acquireLockBlocking(keycloakId, CART_ADD_LOCK_WAIT_MS);
        if (lockToken == null) {
            // Couldn't get the lock in time — surface a retryable error rather than racing.
            log.warn("Could not acquire cart lock for user {} within {}ms — aborting addItemsToCart", keycloakId, CART_ADD_LOCK_WAIT_MS);
            throw new IllegalStateException("Cart is temporarily locked by a concurrent operation, please retry.");
        }

        try {
            // BUG-160 — Run the read-modify-write inside an explicit programmatic transaction
            // so the transaction COMMITS before the lock is released. If the lock were released
            // while a surrounding @Transactional was still open, a second thread could grab
            // the lock and read the pre-commit cart row, causing a lost-update race. Using
            // TransactionTemplate keeps the lifecycle as: acquire-lock -> tx-begin -> mutate ->
            // tx-commit -> release-lock.
            try {
                if (transactionTemplate != null) {
                    return transactionTemplate.execute(status -> doAddItemsToCart(cartCreateRequestDto, keycloakId));
                }
                // Fallback for unit tests where no PlatformTransactionManager is wired in.
                return doAddItemsToCart(cartCreateRequestDto, keycloakId);
            } catch (DataIntegrityViolationException dive) {
                // BUG-160 (PRE-2) — Schema-level UNIQUE(userId) on cartTable closes the
                // first-time-insert race: when two concurrent /cart/add requests for a brand-
                // new user both attempt to INSERT a cart row, the loser hits a unique-violation.
                // We retry exactly once: by now the winning thread has committed, so the cart
                // row exists and the SELECT ... FOR UPDATE in doAddItemsToCart will find it
                // and properly serialise on it. No infinite loop — a second DIVE would imply
                // a different constraint violation and is allowed to propagate.
                log.warn("BUG-160 — concurrent cart insert raced for user {} (DataIntegrityViolation: {}). Retrying once.",
                        keycloakId, dive.getMostSpecificCause() != null ? dive.getMostSpecificCause().getMessage() : dive.getMessage());
                if (transactionTemplate != null) {
                    return transactionTemplate.execute(status -> doAddItemsToCart(cartCreateRequestDto, keycloakId));
                }
                return doAddItemsToCart(cartCreateRequestDto, keycloakId);
            }
        } finally {
            cartCacheHelper.releaseLock(keycloakId, lockToken);
        }
    }

    /**
     * BUG-160 — Core read-modify-write for {@link #addItemsToCart}.
     * <p>
     * Extracted so the lock-acquire / lock-release wrapper in
     * {@link #addItemsToCart(CartCreateRequestDto, String)} stays readable.
     * This method assumes the caller already holds the per-user write lock.
     */
    private CartResponseDto doAddItemsToCart(final CartCreateRequestDto cartCreateRequestDto, final String keycloakId) {
        final Map<UUID, Integer> productsToAdd = cartCreateRequestDto.getCartItemAddRequestDtos().stream().collect(Collectors.toMap(CartItemAddRequestDto::getProductUuid, CartItemAddRequestDto::getQuantity));
        final UserEntity user = userRepository.findByKeycloakId(keycloakId).orElseThrow(() -> new UserNotFoundException("User not found"));

        // Récupération des produits en une seule requête pour optimiser les performances
        List<ProductEntity> products = productRepository.findAllByUuidIn(productsToAdd.keySet());
        Map<UUID, ProductEntity> productMap = products.stream().collect(Collectors.toMap(ProductEntity::getUuid, p -> p));

        log.info("products to add : {}", productsToAdd);
        log.info("productsMap : {}", productMap);
        log.info("products : {}", products);

        // BUG-160 — Prefer a SELECT ... FOR UPDATE on the cart row when one exists,
        // so concurrent /cart/add calls block at the row level for the duration of the
        // transaction. This is the bulletproof second line of defence on top of the
        // Redis lock taken by addItemsToCart: even if the Redis lock were bypassed
        // (cache outage, etc.), the pessimistic DB lock would still serialise the
        // read-modify-write sequence. Falls back to the historical user-side lazy
        // load when the lock query returns nothing (covers brand-new users whose cart
        // row does not yet exist, and the unit-test mock layer which only stubs the
        // user-side load).
        CartEntity cartEntity = cartRepository.findByOwnerKeycloakIdForUpdate(keycloakId)
                .orElseGet(user::getCartEntity);

        log.info("cart : {}", cartEntity);

        // 1. Créer le panier s'il n'existe pas
        if (cartEntity == null) {
            cartEntity = CartEntity.builder()
                    .userEntity(user)
                    .cartItems(new ArrayList<>())
                    .uuid(UuidCreator.getTimeOrderedEpoch())
                    .build();
        }

        // Pour chaque produit à ajouter
        for (Map.Entry<UUID, Integer> entry : productsToAdd.entrySet()) {
            UUID productUuid = entry.getKey();
            Integer quantity = entry.getValue();
            ProductEntity product = productMap.get(productUuid);
            if (product == null) {
                throw new ProductNotFoundException("No product with the UUID : " + productUuid + " found");
            }

            // 2. Vérifier si le produit est déjà dans le panier
            CartEntity finalCartEntity = cartEntity;
            Optional<CartItemEntity> existingItem = finalCartEntity.getCartItems().stream()
                    .filter(item -> item.getProductEntity().getUuid().equals(productUuid))
                    .findFirst();

            log.info("existingItem : {} and old quantity : {}", existingItem, quantity);

            int newQuantity = existingItem.map(item -> {
                int res = item.getQuantity() + quantity;

                log.info("newQuantity in lambda : {}", res);

                return res;

            }).orElse(quantity);

            log.info("newQuantity : {}", newQuantity);

            // 3. Vérifier le stock (Stock total vs Stock réservé + Quantité demandée totale)
            validateStockAvailability(product, newQuantity);

            log.info("product stock : {}", product.getStock());
            log.info("product reserved stock : {}", product.getReservedStock());

            if (existingItem.isPresent()) {
                // Mise à jour de la quantité existante
                log.info("existing item before quantity update: {}", existingItem);
                existingItem.get().increaseQuantity(quantity);
                log.info("existing item after quantity update: {}", existingItem);
            } else {
                // Ajout d'un nouvel item
                final CartItemEntity newItem = CartItemEntity.builder()
                        .quantity(quantity)
                        .unitPrice(product.getPrice())
                        .productEntity(product)
                        .cart(cartEntity) // Important : Lier l'enfant au parent
                        .uuid(UuidCreator.getTimeOrderedEpoch())
                        .build();

                cartEntity.getCartItems().add(newItem);
            }
        }

        CartEntity savedCart = cartRepository.save(cartEntity);
        CartResponseDto cartResponseDto = cartMapper.mapFromEntityToResponseDto(savedCart);
        cartCacheHelper.putWithJitter(keycloakId, cartResponseDto);

        return cartResponseDto;
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

        // 2) Cache miss → try to acquire the rebuild lock
        final String token = cartCacheHelper.acquireLock(keycloakId);

        if (token == null) {
            // Another thread holds the lock and is already rebuilding.
            // Re-read: if they just populated the cache, return it; otherwise return empty.
            final CartResponseDto retry = cartCacheHelper.getRaw(keycloakId);
            return retry != null ? retry : new CartResponseDto();
        }

        try {
            // 3) We hold the lock — rebuild from DB
            final UserEntity user = userRepository.findByKeycloakId(keycloakId).orElseThrow(() -> new UserNotFoundException("User not found"));

            final CartResponseDto dto = user.getCartEntity() == null
                    ? new CartResponseDto()
                    : cartMapper.mapFromEntityToResponseDto(user.getCartEntity());

            // 4) Populate cache with jitter TTL
            cartCacheHelper.putWithJitter(keycloakId, dto);

            return dto;

        } finally {
            // 5) Always release the lock
            log.info("Releasing lock for user {} with token : {}", keycloakId, token);
            cartCacheHelper.releaseLock(keycloakId, token);
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
     * BUG-161 — Ownership-checked read-by-UUID.
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
     * BUG-026 / BUG-161 — New, correctly-typed, ownership-checked cart update.
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

        // BUG-7 — Validate every line (product existence + stock availability) BEFORE
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
                validateStockAvailability(product, line.getQuantity());
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
                        .unitPrice(product.getPrice())
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

    /**
     * BUG-7 — Shared stock-availability check used by both
     * {@link #doAddItemsToCart(CartCreateRequestDto, String)} and
     * {@link #updateCart(UUID, CartUpdateRequestDto, String)}.
     * <p>
     * Throws {@link NotEnoughStockException} when {@code reservedStock + requestedQty > totalStock},
     * with a message that surfaces both the requested and the currently-available
     * quantity so the caller can adjust their request.
     *
     * @param product      product whose stock is being checked.
     * @param requestedQty total quantity the caller wants in the cart for that product.
     * @throws NotEnoughStockException when the request would exceed available stock.
     */
    private static void validateStockAvailability(final ProductEntity product, final int requestedQty) {
        if (product.getReservedStock() + requestedQty > product.getStock()) {
            throw new NotEnoughStockException(
                    "Not enough stock for product " + product.getName()
                            + ". Available: " + (product.getStock() - product.getReservedStock()));
        }
    }

    @Override
    @Transactional
    public void deleteByUUID(UUID uuid) {
        cartRepository.deleteByUuid(uuid);
    }

    /**
     * BUG-161 — Ownership-checked delete-by-UUID.
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
     * BUG-161 — Central ownership guard.
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
            log.warn("BUG-161 — Unauthorized cart access attempt: caller {} on cart {}", keycloakId, cartUuid);
            throw new UnauthorizedCartAccessException("Caller does not own cart " + cartUuid);
        }
    }
}
