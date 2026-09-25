package com.novatech.cybertech.services.implementation.shopping;

import com.novatech.cybertech.dto.request.cart.CartCreateRequestDto;
import com.novatech.cybertech.dto.request.cart.CartItemAddRequestDto;
import com.novatech.cybertech.dto.request.cart.CartItemRemoveRequestDto;
import com.novatech.cybertech.dto.response.cart.CartResponseDto;
import com.novatech.cybertech.entities.CartEntity;
import com.novatech.cybertech.entities.CartItemEntity;
import com.novatech.cybertech.entities.ProductEntity;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.dto.request.cart.CartUpdateRequestDto;
import com.novatech.cybertech.exceptions.CannotRemoveItemFromEmptyCartException;
import com.novatech.cybertech.exceptions.CartIsEmptyException;
import com.novatech.cybertech.exceptions.CartNotFoundException;
import com.novatech.cybertech.exceptions.UnauthorizedCartAccessException;
import com.novatech.cybertech.exceptions.UserNotFoundException;
import com.novatech.cybertech.fixtures.builders.CartEntityBuilder;
import com.novatech.cybertech.fixtures.builders.CartItemEntityBuilder;
import com.novatech.cybertech.fixtures.builders.ProductEntityBuilder;
import com.novatech.cybertech.fixtures.builders.UserEntityBuilder;
import com.novatech.cybertech.mappers.entity.CartMapper;
import com.novatech.cybertech.repositories.CartRepository;
import com.novatech.cybertech.repositories.UserRepository;
import com.novatech.cybertech.services.core.CartCacheHelper;
import com.novatech.cybertech.services.core.CartWriteTransactionalDelegate;
import com.novatech.cybertech.services.implementation.CartServiceImp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Mockito unit tests for {@link CartServiceImp}.
 *
 * <p><b>Cart cluster closures:</b></p>
 * <ul>
 *   <li>New {@link CartUpdateRequestDto} carries the correct
 *       update payload, and {@code updateCart(UUID, CartUpdateRequestDto, keycloakId)}
 *       loads the existing cart, verifies ownership, and replaces its items. The
 *       historical {@code update(CartItemRemoveRequestDto)} stays wired to keep
 *       {@code CrudBaseService} happy.</li>
 *   <li>{@link CartServiceImp#addItemsToCart} now takes a
 *       per-user distributed Redis lock around the full read-modify-write path.</li>
 *   <li>Ownership-checked overloads
 *       {@code getByUUID(UUID, String)} / {@code deleteByUUID(UUID, String)} throw
 *       {@link UnauthorizedCartAccessException} when the caller's Keycloak subject
 *       does not match the cart's owner.</li>
 *   <li>Negative/null quantity is rejected — enforced by
     *       {@code @Valid} bean validation at the controller boundary, covered in
     *       {@code CartManagementControllerTest}; no longer duplicated at the service level.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class CartServiceImpTest {

    @Mock CartMapper cartMapper;
    @Mock UserRepository userRepository;
    @Mock CartRepository cartRepository;
    @Mock CartCacheHelper cartCacheHelper;
    @Mock CartWriteTransactionalDelegate cartWriteTransactionalDelegate;

    @InjectMocks CartServiceImp service;

    String keycloakId;

    @BeforeEach
    void setUp() {
        keycloakId = "kc-" + UUID.randomUUID();
        // addItemsToCart now wraps its read-modify-write in the distributed lock.
        // Use lenient so tests which never exercise addItemsToCart (e.g. getCart, remove,
        // decreaseQuantity, CRUD paths) don't fail with Mockito strict-stubbing.
        lenient().when(cartCacheHelper.acquireLockBlocking(anyString(), anyLong()))
                .thenReturn(true);
    }

    // ---- helpers ---------------------------------------------------

    private CartCreateRequestDto requestFor(UUID productUuid, Integer quantity) {
        return CartCreateRequestDto.builder()
                .cartItemAddRequestDtos(new ArrayList<>(List.of(
                        CartItemAddRequestDto.builder().productUuid(productUuid).quantity(quantity).build())))
                .build();
    }

    private CartCreateRequestDto requestFor(List<CartItemAddRequestDto> items) {
        return CartCreateRequestDto.builder().cartItemAddRequestDtos(new ArrayList<>(items)).build();
    }

    private CartResponseDto stubMappedResponse() {
        return CartResponseDto.builder().cartUuid(UUID.randomUUID()).userUuid(UUID.randomUUID()).build();
    }

    private UserEntity userWithCart(UserEntity user, CartEntity cart) {
        user.setCartEntity(cart);
        return user;
    }

    // =================================================================
    @Nested
    @DisplayName("addItemsToCart — Redis-lock orchestration around the write delegate")
    class AddItemsToCart {

        @Test
        @DisplayName("happy: acquires lock, delegates the RMW, releases lock — strictly in that order")
        void delegatesWithinLock_inOrder() {
            final CartCreateRequestDto req = requestFor(UUID.randomUUID(), 2);
            final CartResponseDto delegated = stubMappedResponse();
            when(cartWriteTransactionalDelegate.addItemsWithinTransaction(req, keycloakId)).thenReturn(delegated);

            final CartResponseDto result = service.addItemsToCart(req, keycloakId);

            assertThat(result).isSameAs(delegated);
            // The commit-before-unlock contract relies on this exact ordering: the delegate (which
            // owns the @Transactional commit) must run strictly between lock acquire and release,
            // and the cache write happens only after the delegate returned (i.e. after commit).
            final InOrder inOrder = inOrder(cartCacheHelper, cartWriteTransactionalDelegate);
            inOrder.verify(cartCacheHelper).acquireLockBlocking(eq(keycloakId), anyLong());
            inOrder.verify(cartWriteTransactionalDelegate).addItemsWithinTransaction(req, keycloakId);
            inOrder.verify(cartCacheHelper).putWithJitter(keycloakId, delegated);
            inOrder.verify(cartCacheHelper).releaseLock(eq(keycloakId));
        }

        @Test
        @DisplayName("lock not acquired within budget -> IllegalStateException, delegate never called, no release")
        void lockTimeout_throws_delegateNotCalled() {
            when(cartCacheHelper.acquireLockBlocking(anyString(), anyLong())).thenReturn(false);
            final CartCreateRequestDto req = requestFor(UUID.randomUUID(), 1);

            assertThatThrownBy(() -> service.addItemsToCart(req, keycloakId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("temporarily locked");

            verifyNoInteractions(cartWriteTransactionalDelegate);
            verify(cartCacheHelper, never()).releaseLock(anyString());
        }

        @Test
        @DisplayName("delegate throws -> exception propagates and the lock is STILL released")
        void delegateThrows_lockStillReleased() {
            final CartCreateRequestDto req = requestFor(UUID.randomUUID(), 1);
            when(cartWriteTransactionalDelegate.addItemsWithinTransaction(req, keycloakId))
                    .thenThrow(new UserNotFoundException("User not found"));

            assertThatThrownBy(() -> service.addItemsToCart(req, keycloakId))
                    .isInstanceOf(UserNotFoundException.class);

            // The Redis lock must never leak, even when the transactional delegate fails.
            verify(cartCacheHelper).releaseLock(eq(keycloakId));
            // No cache write when the delegate never returned a result to cache.
            verify(cartCacheHelper, never()).putWithJitter(anyString(), any());
        }

        // Negative/zero/null quantity coverage moved to
        // CartManagementControllerTest#failAddToCart_whenNegativeQuantity_thenBadRequest: quantity
        // validation lives on CartItemAddRequestDto (@NotNull @Min(1)) and is enforced by @Valid at
        // the controller boundary, so CartServiceImp no longer re-checks it — there was nothing left
        // for this Mockito-level test to exercise.
    }

    // =================================================================
    @Nested
    @DisplayName("getCart")
    class GetCart {

        @Test
        @DisplayName("cache hit returns cached DTO and refreshes TTL (no DB call)")
        void cacheHit_refreshesTtl() {
            final CartResponseDto cached = stubMappedResponse();
            when(cartCacheHelper.getRaw(keycloakId)).thenReturn(cached);

            final CartResponseDto result = service.getCart(keycloakId);

            assertThat(result).isSameAs(cached);
            verify(cartCacheHelper).refreshTtlWithJitter(keycloakId);
            verifyNoInteractions(userRepository, cartRepository);
            verify(cartCacheHelper, never()).acquireLockBlocking(anyString(), anyLong());
        }

        @Test
        @DisplayName("cache miss + lock acquired -> double-check misses too, DB lookup, cache write, lock release")
        void cacheMiss_dbLookupAndCachePut() {
            final ProductEntity product = ProductEntityBuilder.aValidProduct();
            final UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).build();
            final CartItemEntity item = CartItemEntityBuilder.aValidCartItemBuilder().productEntity(product).build();
            final CartEntity cart = CartEntityBuilder.aValidCartBuilder().userEntity(user)
                    .cartItems(new ArrayList<>(List.of(item))).build();
            user.setCartEntity(cart);

            final CartResponseDto mapped = stubMappedResponse();
            // Both the initial probe AND the post-lock double-check miss.
            when(cartCacheHelper.getRaw(keycloakId)).thenReturn(null);
            when(cartCacheHelper.acquireLockBlocking(eq(keycloakId), anyLong())).thenReturn(true);
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));
            when(cartMapper.mapFromEntityToResponseDto(cart)).thenReturn(mapped);

            final CartResponseDto result = service.getCart(keycloakId);

            assertThat(result).isSameAs(mapped);
            verify(cartCacheHelper, times(2)).getRaw(keycloakId);
            verify(cartCacheHelper).putWithJitter(keycloakId, mapped);
            verify(cartCacheHelper).releaseLock(keycloakId);
        }

        @Test
        @DisplayName("cache miss + lock acquired + another thread already rebuilt while waiting -> reuse their result, no DB hit")
        void cacheMiss_lockAcquired_rebuiltWhileWaiting_reusesResult() {
            final CartResponseDto rebuiltByWinner = stubMappedResponse();
            // First probe misses; by the time this caller finally gets the lock, the double-check
            // finds the cache already populated by whoever held the lock before it.
            when(cartCacheHelper.getRaw(keycloakId))
                    .thenReturn(null)
                    .thenReturn(rebuiltByWinner);
            when(cartCacheHelper.acquireLockBlocking(eq(keycloakId), anyLong())).thenReturn(true);

            final CartResponseDto result = service.getCart(keycloakId);

            assertThat(result).isSameAs(rebuiltByWinner);
            // No redundant DB rebuild — the double-check short-circuited before touching the DB.
            verifyNoInteractions(userRepository, cartRepository, cartMapper);
            // Never re-populates the cache — the winner already did.
            verify(cartCacheHelper, never()).putWithJitter(anyString(), any(CartResponseDto.class));
            verify(cartCacheHelper).releaseLock(keycloakId);
        }

        @Test
        @DisplayName("cache miss + user has no cart -> empty CartResponseDto returned and cached")
        void cacheMiss_noCartOnUser_returnsEmpty() {
            final UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).cartEntity(null).build();
            when(cartCacheHelper.getRaw(keycloakId)).thenReturn(null);
            when(cartCacheHelper.acquireLockBlocking(eq(keycloakId), anyLong())).thenReturn(true);
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));

            final CartResponseDto result = service.getCart(keycloakId);

            assertThat(result).isNotNull();
            assertThat(result.getCartUuid()).isNull();
            // putWithJitter still called with the empty DTO
            verify(cartCacheHelper).putWithJitter(eq(keycloakId), any(CartResponseDto.class));
            verify(cartCacheHelper).releaseLock(keycloakId);
        }

        @Test
        @DisplayName("cache miss + lock NOT acquired within budget + retry hit -> returns retry DTO without DB")
        void cacheMiss_lockBusy_retryHit() {
            final CartResponseDto retried = stubMappedResponse();
            when(cartCacheHelper.getRaw(keycloakId))
                    .thenReturn(null)   // first probe miss
                    .thenReturn(retried); // retry hit
            when(cartCacheHelper.acquireLockBlocking(eq(keycloakId), anyLong())).thenReturn(false);

            final CartResponseDto result = service.getCart(keycloakId);

            assertThat(result).isSameAs(retried);
            verifyNoInteractions(userRepository, cartRepository, cartMapper);
            verify(cartCacheHelper, never()).releaseLock(anyString());
        }

        @Test
        @DisplayName("cache miss + lock NOT acquired within budget + retry miss -> empty DTO")
        void cacheMiss_lockBusy_retryMiss_returnsEmpty() {
            when(cartCacheHelper.getRaw(keycloakId))
                    .thenReturn(null)
                    .thenReturn(null);
            when(cartCacheHelper.acquireLockBlocking(eq(keycloakId), anyLong())).thenReturn(false);

            final CartResponseDto result = service.getCart(keycloakId);

            assertThat(result).isNotNull();
            assertThat(result.getCartUuid()).isNull();
            assertThat(result.getItems()).isNull();
        }

        @Test
        @DisplayName("user missing during DB rebuild -> exception bubbles, lock STILL released")
        void userMissingDuringRebuild_releasesLock() {
            when(cartCacheHelper.getRaw(keycloakId)).thenReturn(null);
            when(cartCacheHelper.acquireLockBlocking(eq(keycloakId), anyLong())).thenReturn(true);
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getCart(keycloakId))
                    .isInstanceOf(UserNotFoundException.class);

            verify(cartCacheHelper).releaseLock(keycloakId);
        }
    }

    // =================================================================
    // removeItemFromCart / decreaseQuantity / clearCart / updateCart are now thin wrappers around
    // cartWriteTransactionalDelegate — the read-modify-write logic (empty-cart checks, item lookup,
    // stock validation, ownership) is covered directly against the delegate in
    // CartWriteTransactionalDelegateTest. What's left to verify here is the wrapper's own job: call
    // the delegate, then write the cache with whatever it returned (or skip the cache write when
    // there's nothing to cache, e.g. clearCart on a user with no cart).

    @Nested
    @DisplayName("removeItemFromCart")
    class RemoveItemFromCart {

        @Test
        @DisplayName("delegates the removal, then caches the returned cart")
        void delegatesAndCaches() {
            final UUID productUuid = UUID.randomUUID();
            final CartResponseDto delegated = stubMappedResponse();
            when(cartWriteTransactionalDelegate.removeItemWithinTransaction(productUuid, keycloakId)).thenReturn(delegated);

            final CartResponseDto result = service.removeItemFromCart(productUuid, keycloakId);

            assertThat(result).isSameAs(delegated);
            verify(cartCacheHelper).putWithJitter(keycloakId, delegated);
        }

        @Test
        @DisplayName("delegate throws -> exception propagates, cache untouched")
        void delegateThrows_propagatesNoCache() {
            final UUID productUuid = UUID.randomUUID();
            when(cartWriteTransactionalDelegate.removeItemWithinTransaction(productUuid, keycloakId))
                    .thenThrow(new CannotRemoveItemFromEmptyCartException("nope"));

            assertThatThrownBy(() -> service.removeItemFromCart(productUuid, keycloakId))
                    .isInstanceOf(CannotRemoveItemFromEmptyCartException.class);

            verify(cartCacheHelper, never()).putWithJitter(anyString(), any());
        }
    }

    // =================================================================
    @Nested
    @DisplayName("decreaseQuantity")
    class DecreaseQuantity {

        private CartItemRemoveRequestDto removeReq(UUID productUuid, int qty) {
            return CartItemRemoveRequestDto.builder().productUuid(productUuid).quantity(qty).build();
        }

        @Test
        @DisplayName("delegates the decrease, then caches the returned cart")
        void delegatesAndCaches() {
            final CartItemRemoveRequestDto req = removeReq(UUID.randomUUID(), 2);
            final CartResponseDto delegated = stubMappedResponse();
            when(cartWriteTransactionalDelegate.decreaseQuantityWithinTransaction(req, keycloakId)).thenReturn(delegated);

            final CartResponseDto result = service.decreaseQuantity(req, keycloakId);

            assertThat(result).isSameAs(delegated);
            verify(cartCacheHelper).putWithJitter(keycloakId, delegated);
        }

        @Test
        @DisplayName("delegate throws -> exception propagates, cache untouched")
        void delegateThrows_propagatesNoCache() {
            final CartItemRemoveRequestDto req = removeReq(UUID.randomUUID(), 1);
            when(cartWriteTransactionalDelegate.decreaseQuantityWithinTransaction(req, keycloakId))
                    .thenThrow(new CartIsEmptyException("nope"));

            assertThatThrownBy(() -> service.decreaseQuantity(req, keycloakId))
                    .isInstanceOf(CartIsEmptyException.class);

            verify(cartCacheHelper, never()).putWithJitter(anyString(), any());
        }
    }

    // =================================================================
    @Nested
    @DisplayName("clearCart")
    class ClearCart {

        @Test
        @DisplayName("delegates the clear, then caches the returned (emptied) cart")
        void delegatesAndCaches() {
            final CartResponseDto delegated = stubMappedResponse();
            when(cartWriteTransactionalDelegate.clearCartWithinTransaction(keycloakId)).thenReturn(delegated);

            service.clearCart(keycloakId);

            verify(cartCacheHelper).putWithJitter(keycloakId, delegated);
        }

        @Test
        @DisplayName("delegate returns null (no cart to clear) -> cache left untouched")
        void delegateReturnsNull_skipsCache() {
            when(cartWriteTransactionalDelegate.clearCartWithinTransaction(keycloakId)).thenReturn(null);

            service.clearCart(keycloakId);

            verify(cartCacheHelper, never()).putWithJitter(anyString(), any());
        }

        @Test
        @DisplayName("delegate throws -> exception propagates, cache untouched")
        void delegateThrows_propagatesNoCache() {
            when(cartWriteTransactionalDelegate.clearCartWithinTransaction(keycloakId))
                    .thenThrow(new UserNotFoundException("nope"));

            assertThatThrownBy(() -> service.clearCart(keycloakId)).isInstanceOf(UserNotFoundException.class);

            verify(cartCacheHelper, never()).putWithJitter(anyString(), any());
        }
    }

    // =================================================================
    @Nested
    @DisplayName("CRUD admin: getAll / getByUUID / create / update / deleteByUUID")
    class CrudAdmin {

        @Test
        @DisplayName("getAll delegates to repository.findAllWithItemsAndProducts and maps")
        void getAll_happy() {
            final List<CartEntity> all = List.of(CartEntityBuilder.aValidCart());
            final List<CartResponseDto> dtos = List.of(new CartResponseDto());
            when(cartRepository.findAllWithItemsAndProducts()).thenReturn(all);
            when(cartMapper.mapFromEntityToResponseDto((Collection<CartEntity>) all))
                    .thenReturn((Collection<CartResponseDto>) (Collection<?>) dtos);

            assertThat(service.getAll()).isEqualTo(dtos);
        }

        @Test
        @DisplayName("getByUUID happy returns mapped DTO")
        void getByUuid_happy() {
            final UUID uuid = UUID.randomUUID();
            final CartEntity cart = CartEntityBuilder.aValidCartBuilder().uuid(uuid).build();
            final CartResponseDto resp = new CartResponseDto();
            when(cartRepository.findByUuid(uuid)).thenReturn(Optional.of(cart));
            when(cartMapper.mapFromEntityToResponseDto(cart)).thenReturn(resp);

            assertThat(service.getByUUID(uuid)).isSameAs(resp);
        }

        @Test
        @DisplayName("getByUUID missing -> CartNotFoundException")
        void getByUuid_missing_throws() {
            final UUID uuid = UUID.randomUUID();
            when(cartRepository.findByUuid(uuid)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getByUUID(uuid))
                    .isInstanceOf(CartNotFoundException.class)
                    .hasMessageContaining(uuid.toString());
        }

        @Test
        @DisplayName("create() maps creation request, saves, maps response")
        void create_happy() {
            final CartCreateRequestDto req = CartCreateRequestDto.builder()
                    .cartItemAddRequestDtos(List.of()).build();
            final CartEntity entity = CartEntityBuilder.aValidCart();
            final CartEntity saved = CartEntityBuilder.aValidCart();
            final CartResponseDto resp = new CartResponseDto();

            when(cartMapper.mapFromCreationRequestToEntity(req)).thenReturn(entity);
            when(cartRepository.save(entity)).thenReturn(saved);
            when(cartMapper.mapFromEntityToResponseDto(saved)).thenReturn(resp);

            assertThat(service.create(req)).isSameAs(resp);
        }

        @Test
        @DisplayName("deleteByUUID delegates to repository.deleteByUuid")
        void deleteByUuid_delegates() {
            final UUID uuid = UUID.randomUUID();
            service.deleteByUUID(uuid);
            verify(cartRepository).deleteByUuid(uuid);
        }

        @Test
        @DisplayName("updateCart(UUID, CartUpdateRequestDto, keycloakId) delegates, then caches the result")
        void updateCart_delegatesAndCaches() {
            final UUID cartUuid = UUID.randomUUID();
            final CartUpdateRequestDto dto = CartUpdateRequestDto.builder()
                    .cartItemAddRequestDtos(new ArrayList<>()).build();
            final CartResponseDto delegated = stubMappedResponse();
            when(cartWriteTransactionalDelegate.updateCartWithinTransaction(cartUuid, dto, keycloakId)).thenReturn(delegated);

            final CartResponseDto result = service.updateCart(cartUuid, dto, keycloakId);

            assertThat(result).isSameAs(delegated);
            verify(cartCacheHelper).putWithJitter(keycloakId, delegated);
        }

        @Test
        @DisplayName("updateCart: delegate throws -> exception propagates, cache untouched")
        void updateCart_delegateThrows_propagatesNoCache() {
            final UUID cartUuid = UUID.randomUUID();
            final CartUpdateRequestDto dto = CartUpdateRequestDto.builder()
                    .cartItemAddRequestDtos(new ArrayList<>()).build();
            when(cartWriteTransactionalDelegate.updateCartWithinTransaction(cartUuid, dto, keycloakId))
                    .thenThrow(new UnauthorizedCartAccessException("nope"));

            assertThatThrownBy(() -> service.updateCart(cartUuid, dto, keycloakId))
                    .isInstanceOf(UnauthorizedCartAccessException.class);

            verify(cartCacheHelper, never()).putWithJitter(anyString(), any());
        }

        @Test
        @DisplayName("Legacy update(CartItemRemoveRequestDto) preserved for base CRUD contract")
        void update_currentBehavior_PIN() {
            // Intentionally kept: the generic CrudBaseService contract still points at
            // update(CartItemRemoveRequestDto). The correct fix surface is the new
            // updateCart(...) overload above — this test guarantees the legacy method
            // stays wired so external CRUD callers don't break.
            final CartItemRemoveRequestDto dto = CartItemRemoveRequestDto.builder()
                    .productUuid(UUID.randomUUID()).quantity(1).build();
            final CartEntity entity = CartEntityBuilder.aValidCart();
            final CartEntity saved = CartEntityBuilder.aValidCart();
            final CartResponseDto resp = new CartResponseDto();

            when(cartMapper.mapFromUpdateRequestToEntity(dto)).thenReturn(entity);
            when(cartRepository.save(entity)).thenReturn(saved);
            when(cartMapper.mapFromEntityToResponseDto(saved)).thenReturn(resp);

            assertThat(service.update(dto)).isSameAs(resp);
        }

        @Test
        @DisplayName("getByUUID(UUID, keycloakId) rejects non-owner with " +
                "UnauthorizedCartAccessException")
        void getByUuid_shouldEnforceOwnership_BUG160_closed() {
            final UUID uuid = UUID.randomUUID();
            final UserEntity otherUser = UserEntityBuilder.aValidUserBuilder().keycloakId("OTHER").build();
            final CartEntity cart = CartEntityBuilder.aValidCartBuilder()
                    .uuid(uuid).userEntity(otherUser).cartItems(new ArrayList<>()).build();
            when(cartRepository.findByUuid(uuid)).thenReturn(Optional.of(cart));

            assertThatThrownBy(() -> service.getByUUID(uuid, keycloakId))
                    .isInstanceOf(UnauthorizedCartAccessException.class);
        }

        @Test
        @DisplayName("getByUUID(UUID, keycloakId) returns mapped DTO for owner")
        void getByUuid_owner_returnsMapped_BUG160_closed() {
            final UUID uuid = UUID.randomUUID();
            final UserEntity owner = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).build();
            final CartEntity cart = CartEntityBuilder.aValidCartBuilder()
                    .uuid(uuid).userEntity(owner).cartItems(new ArrayList<>()).build();
            final CartResponseDto resp = stubMappedResponse();
            when(cartRepository.findByUuid(uuid)).thenReturn(Optional.of(cart));
            when(cartMapper.mapFromEntityToResponseDto(cart)).thenReturn(resp);

            assertThat(service.getByUUID(uuid, keycloakId)).isSameAs(resp);
        }

        @Test
        @DisplayName("Legacy getByUUID(UUID) still honored for CrudBaseService contract")
        void getByUuid_noOwnershipCheck_PIN() {
            // The single-arg getByUUID is intentionally preserved to keep the
            // CrudBaseService generic contract. External code wanting ownership
            // enforcement must use the new two-arg overload.
            final UUID uuid = UUID.randomUUID();
            final UserEntity otherUser = UserEntityBuilder.aValidUserBuilder().keycloakId("OTHER").build();
            final CartEntity cart = CartEntityBuilder.aValidCartBuilder().uuid(uuid).userEntity(otherUser).build();
            final CartResponseDto resp = new CartResponseDto();
            when(cartRepository.findByUuid(uuid)).thenReturn(Optional.of(cart));
            when(cartMapper.mapFromEntityToResponseDto(cart)).thenReturn(resp);

            assertThat(service.getByUUID(uuid)).isSameAs(resp);
        }

        @Test
        @DisplayName("deleteByUUID(UUID, keycloakId) rejects non-owner")
        void deleteByUuid_shouldEnforceOwnership_BUG161_closed() {
            final UUID uuid = UUID.randomUUID();
            final UserEntity otherUser = UserEntityBuilder.aValidUserBuilder().keycloakId("OTHER").build();
            final CartEntity cart = CartEntityBuilder.aValidCartBuilder()
                    .uuid(uuid).userEntity(otherUser).cartItems(new ArrayList<>()).build();
            when(cartRepository.findByUuid(uuid)).thenReturn(Optional.of(cart));

            assertThatThrownBy(() -> service.deleteByUUID(uuid, keycloakId))
                    .isInstanceOf(UnauthorizedCartAccessException.class);

            verify(cartRepository, never()).deleteByUuid(any());
        }

        @Test
        @DisplayName("deleteByUUID(UUID, keycloakId) deletes when caller is owner")
        void deleteByUuid_owner_deletes_BUG161_closed() {
            final UUID uuid = UUID.randomUUID();
            final UserEntity owner = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).build();
            final CartEntity cart = CartEntityBuilder.aValidCartBuilder()
                    .uuid(uuid).userEntity(owner).cartItems(new ArrayList<>()).build();
            when(cartRepository.findByUuid(uuid)).thenReturn(Optional.of(cart));

            service.deleteByUUID(uuid, keycloakId);

            verify(cartRepository).deleteByUuid(uuid);
        }

        @Test
        @DisplayName("DeleteByUUID(UUID) takes no caller identity -> trivial IDOR shape pinned")
        void deleteByUuid_noCallerArg_PIN() {
            final UUID uuid = UUID.randomUUID();
            service.deleteByUUID(uuid);
            verify(cartRepository).deleteByUuid(uuid); // happens regardless of who's calling
        }
    }
}
