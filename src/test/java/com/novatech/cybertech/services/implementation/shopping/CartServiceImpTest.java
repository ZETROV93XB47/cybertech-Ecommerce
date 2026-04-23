package com.novatech.cybertech.services.implementation.shopping;

import com.novatech.cybertech.dto.request.cart.CartCreateRequestDto;
import com.novatech.cybertech.dto.request.cart.CartItemAddRequestDto;
import com.novatech.cybertech.dto.request.cart.CartItemRemoveRequestDto;
import com.novatech.cybertech.dto.response.cart.CartResponseDto;
import com.novatech.cybertech.entities.CartEntity;
import com.novatech.cybertech.entities.CartItemEntity;
import com.novatech.cybertech.entities.ProductEntity;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.exceptions.CannotRemoveItemFromEmptyCartException;
import com.novatech.cybertech.exceptions.CartIsEmptyException;
import com.novatech.cybertech.exceptions.CartItemNotFoundException;
import com.novatech.cybertech.exceptions.CartNotFoundException;
import com.novatech.cybertech.exceptions.NegativeQuantityException;
import com.novatech.cybertech.exceptions.NotEnoughStockException;
import com.novatech.cybertech.exceptions.ProductNotFoundException;
import com.novatech.cybertech.exceptions.UserNotFoundException;
import com.novatech.cybertech.fixtures.builders.CartEntityBuilder;
import com.novatech.cybertech.fixtures.builders.CartItemEntityBuilder;
import com.novatech.cybertech.fixtures.builders.ProductEntityBuilder;
import com.novatech.cybertech.fixtures.builders.UserEntityBuilder;
import com.novatech.cybertech.mappers.entity.CartMapper;
import com.novatech.cybertech.repositories.CartRepository;
import com.novatech.cybertech.repositories.ProductRepository;
import com.novatech.cybertech.repositories.UserRepository;
import com.novatech.cybertech.services.core.CartCacheHelper;
import com.novatech.cybertech.services.implementation.CartServiceImp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Mockito unit tests for {@link CartServiceImp}.
 *
 * <p><b>SA-W3.4 wave — Source verification of F1/F2 claims:</b></p>
 * <ul>
 *   <li><b>BUG-039 (Wave F2)</b>: CONFIRMED CLOSED. Source lines 50-52 throw
 *       {@link NegativeQuantityException} for {@code quantity == null || quantity &lt; 1}. Verified.</li>
 *   <li><b>BUG-026 (Wave F1)</b>: REFUTED. F1 claimed a new {@code CartUpdateRequestDto} replaced
 *       {@link CartItemRemoveRequestDto} on {@code update(...)} — no such DTO exists in src/main.
 *       The signature is still {@code update(CartItemRemoveRequestDto)} and still routes through
 *       {@code mapFromUpdateRequestToEntity} which is the bug. Pinned.</li>
 *   <li><b>BUG-160 / BUG-161 (Wave F1)</b>: REFUTED. F1 claimed a new
 *       {@code UnauthorizedCartAccessException} guarded {@code getByUUID}/{@code deleteByUUID}.
 *       No such class exists; methods perform no ownership check. Pinned via @Disabled +
 *       passing PIN tests showing current insecure behavior.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class CartServiceImpTest {

    @Mock CartMapper cartMapper;
    @Mock UserRepository userRepository;
    @Mock CartRepository cartRepository;
    @Mock CartCacheHelper cartCacheHelper;
    @Mock ProductRepository productRepository;

    @InjectMocks CartServiceImp service;

    String keycloakId;

    @BeforeEach
    void setUp() {
        keycloakId = "kc-" + UUID.randomUUID();
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
    @DisplayName("addItemsToCart")
    class AddItemsToCart {

        @Test
        @DisplayName("happy single product into empty cart -> creates cart, adds item, saves, caches")
        void singleProduct_intoEmptyCart_savesAndCaches() {
            final ProductEntity product = ProductEntityBuilder.aValidProductBuilder().stock(10).reservedStock(0).build();
            final UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).cartEntity(null).build();
            final CartCreateRequestDto req = requestFor(product.getUuid(), 2);

            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));
            when(productRepository.findAllByUuidIn(anyCollection())).thenReturn(List.of(product));
            when(cartRepository.save(any(CartEntity.class))).thenAnswer(inv -> inv.getArgument(0));
            final CartResponseDto resp = stubMappedResponse();
            when(cartMapper.mapFromEntityToResponseDto(any(CartEntity.class))).thenReturn(resp);

            final CartResponseDto result = service.addItemsToCart(req, keycloakId);

            assertThat(result).isSameAs(resp);
            final ArgumentCaptor<CartEntity> savedCart = ArgumentCaptor.forClass(CartEntity.class);
            verify(cartRepository).save(savedCart.capture());
            assertThat(savedCart.getValue().getCartItems()).hasSize(1);
            assertThat(savedCart.getValue().getCartItems().get(0).getQuantity()).isEqualTo(2);
            verify(cartCacheHelper).putWithJitter(keycloakId, resp);
        }

        @Test
        @DisplayName("happy multi-product saves all items in one cart")
        void multipleProducts_addsAllItems() {
            final ProductEntity p1 = ProductEntityBuilder.aValidProductBuilder().stock(10).build();
            final ProductEntity p2 = ProductEntityBuilder.aValidProductBuilder().stock(5).build();
            final UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).cartEntity(null).build();
            final CartCreateRequestDto req = requestFor(List.of(
                    CartItemAddRequestDto.builder().productUuid(p1.getUuid()).quantity(1).build(),
                    CartItemAddRequestDto.builder().productUuid(p2.getUuid()).quantity(3).build()));

            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));
            when(productRepository.findAllByUuidIn(anyCollection())).thenReturn(List.of(p1, p2));
            when(cartRepository.save(any(CartEntity.class))).thenAnswer(inv -> inv.getArgument(0));
            when(cartMapper.mapFromEntityToResponseDto(any(CartEntity.class))).thenReturn(stubMappedResponse());

            service.addItemsToCart(req, keycloakId);

            final ArgumentCaptor<CartEntity> captor = ArgumentCaptor.forClass(CartEntity.class);
            verify(cartRepository).save(captor.capture());
            assertThat(captor.getValue().getCartItems()).hasSize(2);
        }

        @Test
        @DisplayName("idempotent re-add: existing item quantity increased rather than duplicated")
        void reAdd_increasesExistingItemQuantity() {
            final ProductEntity product = ProductEntityBuilder.aValidProductBuilder().stock(10).build();
            final UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).build();
            final CartItemEntity existing = CartItemEntityBuilder.aValidCartItemBuilder()
                    .productEntity(product).quantity(2).build();
            final CartEntity cart = CartEntityBuilder.aValidCartBuilder()
                    .userEntity(user)
                    .cartItems(new ArrayList<>(List.of(existing))).build();
            user.setCartEntity(cart);

            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));
            when(productRepository.findAllByUuidIn(anyCollection())).thenReturn(List.of(product));
            when(cartRepository.save(any(CartEntity.class))).thenAnswer(inv -> inv.getArgument(0));
            when(cartMapper.mapFromEntityToResponseDto(any(CartEntity.class))).thenReturn(stubMappedResponse());

            service.addItemsToCart(requestFor(product.getUuid(), 3), keycloakId);

            assertThat(cart.getCartItems()).hasSize(1);
            assertThat(cart.getCartItems().get(0).getQuantity()).isEqualTo(5);
        }

        @Test
        @DisplayName("product missing in repo lookup -> ProductNotFoundException")
        void productMissing_throws() {
            final UUID missingUuid = UUID.randomUUID();
            final UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).cartEntity(null).build();
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));
            when(productRepository.findAllByUuidIn(anyCollection())).thenReturn(List.of()); // nothing returned

            assertThatThrownBy(() -> service.addItemsToCart(requestFor(missingUuid, 1), keycloakId))
                    .isInstanceOf(ProductNotFoundException.class)
                    .hasMessageContaining(missingUuid.toString());

            verify(cartRepository, never()).save(any());
            verify(cartCacheHelper, never()).putWithJitter(anyString(), any());
        }

        @Test
        @DisplayName("user missing -> UserNotFoundException, no save/cache")
        void userMissing_throws() {
            // Note: productRepository.findAllByUuidIn IS called BEFORE userRepository.findByKeycloakId
            // (see CartServiceImp lines 59 vs 56 — actually findByKeycloakId at line 56, findAllByUuidIn at 59).
            // userRepo throws first; product repo not invoked.
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.addItemsToCart(requestFor(UUID.randomUUID(), 1), keycloakId))
                    .isInstanceOf(UserNotFoundException.class);

            verify(cartRepository, never()).save(any());
            verifyNoInteractions(cartCacheHelper);
        }

        @Test
        @DisplayName("cart missing on user -> creates new cart linked to user")
        void cartMissing_createsNewCart() {
            final ProductEntity product = ProductEntityBuilder.aValidProductBuilder().stock(10).build();
            final UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).cartEntity(null).build();
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));
            when(productRepository.findAllByUuidIn(anyCollection())).thenReturn(List.of(product));
            when(cartRepository.save(any(CartEntity.class))).thenAnswer(inv -> inv.getArgument(0));
            when(cartMapper.mapFromEntityToResponseDto(any(CartEntity.class))).thenReturn(stubMappedResponse());

            service.addItemsToCart(requestFor(product.getUuid(), 1), keycloakId);

            final ArgumentCaptor<CartEntity> captor = ArgumentCaptor.forClass(CartEntity.class);
            verify(cartRepository).save(captor.capture());
            assertThat(captor.getValue().getUuid()).isNotNull();
            assertThat(captor.getValue().getUserEntity()).isSameAs(user);
        }

        @Test
        @DisplayName("BUG-039 (F2 verified CLOSED): negative quantity throws NegativeQuantityException")
        void negativeQuantity_throws_BUG039_closed() {
            final UUID productUuid = UUID.randomUUID();
            final CartCreateRequestDto req = requestFor(productUuid, -1);

            assertThatThrownBy(() -> service.addItemsToCart(req, keycloakId))
                    .isInstanceOf(NegativeQuantityException.class)
                    .hasMessageContaining("Quantity must be >= 1");

            verifyNoInteractions(userRepository, productRepository, cartRepository, cartCacheHelper);
        }

        @Test
        @DisplayName("BUG-039: zero quantity also rejected")
        void zeroQuantity_throws_BUG039_closed() {
            assertThatThrownBy(() -> service.addItemsToCart(requestFor(UUID.randomUUID(), 0), keycloakId))
                    .isInstanceOf(NegativeQuantityException.class);
            verifyNoInteractions(userRepository, productRepository, cartRepository);
        }

        @Test
        @DisplayName("BUG-039: null quantity also rejected")
        void nullQuantity_throws_BUG039_closed() {
            assertThatThrownBy(() -> service.addItemsToCart(requestFor(UUID.randomUUID(), null), keycloakId))
                    .isInstanceOf(NegativeQuantityException.class);
        }

        @Test
        @DisplayName("not enough stock -> NotEnoughStockException, no save")
        void notEnoughStock_throws() {
            final ProductEntity product = ProductEntityBuilder.aValidProductBuilder().stock(2).reservedStock(0).build();
            final UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).cartEntity(null).build();
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));
            when(productRepository.findAllByUuidIn(anyCollection())).thenReturn(List.of(product));

            assertThatThrownBy(() -> service.addItemsToCart(requestFor(product.getUuid(), 5), keycloakId))
                    .isInstanceOf(NotEnoughStockException.class);

            verify(cartRepository, never()).save(any());
        }

        @Test
        @DisplayName("not enough stock considers reservedStock + new quantity vs total stock")
        void notEnoughStock_considersReservedStock() {
            final ProductEntity product = ProductEntityBuilder.aValidProductBuilder().stock(10).reservedStock(8).build();
            final UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).cartEntity(null).build();
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));
            when(productRepository.findAllByUuidIn(anyCollection())).thenReturn(List.of(product));

            assertThatThrownBy(() -> service.addItemsToCart(requestFor(product.getUuid(), 3), keycloakId))
                    .isInstanceOf(NotEnoughStockException.class);

            verify(cartRepository, never()).save(any());
        }

        @Test
        @DisplayName("re-add boundary: stock exactly equals reserved+existing+new -> succeeds")
        void boundaryStock_ok() {
            final ProductEntity product = ProductEntityBuilder.aValidProductBuilder().stock(5).reservedStock(0).build();
            final UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).build();
            final CartItemEntity existing = CartItemEntityBuilder.aValidCartItemBuilder().productEntity(product).quantity(2).build();
            final CartEntity cart = CartEntityBuilder.aValidCartBuilder().userEntity(user)
                    .cartItems(new ArrayList<>(List.of(existing))).build();
            user.setCartEntity(cart);

            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));
            when(productRepository.findAllByUuidIn(anyCollection())).thenReturn(List.of(product));
            when(cartRepository.save(any(CartEntity.class))).thenAnswer(inv -> inv.getArgument(0));
            when(cartMapper.mapFromEntityToResponseDto(any(CartEntity.class))).thenReturn(stubMappedResponse());

            // existing(2) + new(3) = 5 == stock(5) -> ok
            service.addItemsToCart(requestFor(product.getUuid(), 3), keycloakId);

            assertThat(cart.getCartItems()).hasSize(1);
            assertThat(cart.getCartItems().get(0).getQuantity()).isEqualTo(5);
        }
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
            verify(cartCacheHelper, never()).acquireLock(anyString());
        }

        @Test
        @DisplayName("cache miss + lock acquired -> DB lookup, cache write, lock release")
        void cacheMiss_dbLookupAndCachePut() {
            final ProductEntity product = ProductEntityBuilder.aValidProduct();
            final UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).build();
            final CartItemEntity item = CartItemEntityBuilder.aValidCartItemBuilder().productEntity(product).build();
            final CartEntity cart = CartEntityBuilder.aValidCartBuilder().userEntity(user)
                    .cartItems(new ArrayList<>(List.of(item))).build();
            user.setCartEntity(cart);

            final CartResponseDto mapped = stubMappedResponse();
            when(cartCacheHelper.getRaw(keycloakId)).thenReturn(null);
            when(cartCacheHelper.acquireLock(keycloakId)).thenReturn("lock-token");
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));
            when(cartMapper.mapFromEntityToResponseDto(cart)).thenReturn(mapped);

            final CartResponseDto result = service.getCart(keycloakId);

            assertThat(result).isSameAs(mapped);
            verify(cartCacheHelper).putWithJitter(keycloakId, mapped);
            verify(cartCacheHelper).releaseLock(keycloakId, "lock-token");
        }

        @Test
        @DisplayName("cache miss + user has no cart -> empty CartResponseDto returned and cached")
        void cacheMiss_noCartOnUser_returnsEmpty() {
            final UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).cartEntity(null).build();
            when(cartCacheHelper.getRaw(keycloakId)).thenReturn(null);
            when(cartCacheHelper.acquireLock(keycloakId)).thenReturn("tok");
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));

            final CartResponseDto result = service.getCart(keycloakId);

            assertThat(result).isNotNull();
            assertThat(result.getCartUuid()).isNull();
            // putWithJitter still called with the empty DTO
            verify(cartCacheHelper).putWithJitter(eq(keycloakId), any(CartResponseDto.class));
            verify(cartCacheHelper).releaseLock(keycloakId, "tok");
        }

        @Test
        @DisplayName("cache miss + lock NOT acquired + retry hit -> returns retry DTO without DB")
        void cacheMiss_lockBusy_retryHit() {
            final CartResponseDto retried = stubMappedResponse();
            when(cartCacheHelper.getRaw(keycloakId))
                    .thenReturn(null)   // first probe miss
                    .thenReturn(retried); // retry hit
            when(cartCacheHelper.acquireLock(keycloakId)).thenReturn(null);

            final CartResponseDto result = service.getCart(keycloakId);

            assertThat(result).isSameAs(retried);
            verifyNoInteractions(userRepository, cartRepository, cartMapper);
            verify(cartCacheHelper, never()).releaseLock(anyString(), anyString());
        }

        @Test
        @DisplayName("cache miss + lock NOT acquired + retry miss -> empty DTO")
        void cacheMiss_lockBusy_retryMiss_returnsEmpty() {
            when(cartCacheHelper.getRaw(keycloakId))
                    .thenReturn(null)
                    .thenReturn(null);
            when(cartCacheHelper.acquireLock(keycloakId)).thenReturn(null);

            final CartResponseDto result = service.getCart(keycloakId);

            assertThat(result).isNotNull();
            assertThat(result.getCartUuid()).isNull();
            assertThat(result.getItems()).isNull();
        }

        @Test
        @DisplayName("user missing during DB rebuild -> exception bubbles, lock STILL released")
        void userMissingDuringRebuild_releasesLock() {
            when(cartCacheHelper.getRaw(keycloakId)).thenReturn(null);
            when(cartCacheHelper.acquireLock(keycloakId)).thenReturn("tok");
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getCart(keycloakId))
                    .isInstanceOf(UserNotFoundException.class);

            verify(cartCacheHelper).releaseLock(keycloakId, "tok");
        }
    }

    // =================================================================
    @Nested
    @DisplayName("removeItemFromCart")
    class RemoveItemFromCart {

        @Test
        @DisplayName("happy: removes item, saves cart, caches new state")
        void happy_removes() {
            final ProductEntity product = ProductEntityBuilder.aValidProduct();
            final UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).build();
            final CartItemEntity item = CartItemEntityBuilder.aValidCartItemBuilder().productEntity(product).build();
            final CartEntity cart = CartEntityBuilder.aValidCartBuilder().userEntity(user)
                    .cartItems(new ArrayList<>(List.of(item))).build();
            user.setCartEntity(cart);

            final CartResponseDto resp = stubMappedResponse();
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));
            when(cartRepository.save(cart)).thenReturn(cart);
            when(cartMapper.mapFromEntityToResponseDto(cart)).thenReturn(resp);

            final CartResponseDto result = service.removeItemFromCart(product.getUuid(), keycloakId);

            assertThat(result).isSameAs(resp);
            assertThat(cart.getCartItems()).isEmpty();
            verify(cartCacheHelper).putWithJitter(keycloakId, resp);
        }

        @Test
        @DisplayName("last-item path: cart now empty after removing only item")
        void lastItem_cartBecomesEmpty() {
            final ProductEntity product = ProductEntityBuilder.aValidProduct();
            final UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).build();
            final CartItemEntity sole = CartItemEntityBuilder.aValidCartItemBuilder().productEntity(product).build();
            final CartEntity cart = CartEntityBuilder.aValidCartBuilder().userEntity(user)
                    .cartItems(new ArrayList<>(List.of(sole))).build();
            user.setCartEntity(cart);

            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));
            when(cartRepository.save(cart)).thenReturn(cart);
            when(cartMapper.mapFromEntityToResponseDto(cart)).thenReturn(stubMappedResponse());

            service.removeItemFromCart(product.getUuid(), keycloakId);
            assertThat(cart.getCartItems()).isEmpty();
        }

        @Test
        @DisplayName("removing nonexistent product from non-empty cart throws CannotRemoveItemFromEmptyCartException")
        void nonexistentInNonEmpty_throws() {
            final ProductEntity inCartProduct = ProductEntityBuilder.aValidProduct();
            final UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).build();
            final CartItemEntity item = CartItemEntityBuilder.aValidCartItemBuilder().productEntity(inCartProduct).build();
            final CartEntity cart = CartEntityBuilder.aValidCartBuilder().userEntity(user)
                    .cartItems(new ArrayList<>(List.of(item))).build();
            user.setCartEntity(cart);

            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> service.removeItemFromCart(UUID.randomUUID(), keycloakId))
                    .isInstanceOf(CannotRemoveItemFromEmptyCartException.class);
            verify(cartRepository, never()).save(any());
        }

        @Test
        @DisplayName("user has no cart -> CannotRemoveItemFromEmptyCartException")
        void noCart_throws() {
            final UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).cartEntity(null).build();
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> service.removeItemFromCart(UUID.randomUUID(), keycloakId))
                    .isInstanceOf(CannotRemoveItemFromEmptyCartException.class);
        }

        @Test
        @DisplayName("user missing -> UserNotFoundException")
        void userMissing_throws() {
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.removeItemFromCart(UUID.randomUUID(), keycloakId))
                    .isInstanceOf(UserNotFoundException.class);
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
        @DisplayName("happy: decrease keeps a positive remaining quantity, item NOT removed")
        void decreasePositive_keepsItem() {
            final ProductEntity product = ProductEntityBuilder.aValidProduct();
            final UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).build();
            final CartItemEntity item = CartItemEntityBuilder.aValidCartItemBuilder().productEntity(product).quantity(5).build();
            final CartEntity cart = CartEntityBuilder.aValidCartBuilder().userEntity(user)
                    .cartItems(new ArrayList<>(List.of(item))).build();
            user.setCartEntity(cart);

            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));
            when(cartRepository.save(cart)).thenReturn(cart);
            when(cartMapper.mapFromEntityToResponseDto(cart)).thenReturn(stubMappedResponse());

            service.decreaseQuantity(removeReq(product.getUuid(), 2), keycloakId);

            assertThat(cart.getCartItems()).hasSize(1);
            assertThat(cart.getCartItems().get(0).getQuantity()).isEqualTo(3);
        }

        @Test
        @DisplayName("decrease-to-zero auto-removes item from cart list")
        void decreaseToZero_autoRemoves() {
            final ProductEntity product = ProductEntityBuilder.aValidProduct();
            final UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).build();
            final CartItemEntity item = CartItemEntityBuilder.aValidCartItemBuilder().productEntity(product).quantity(2).build();
            final CartEntity cart = CartEntityBuilder.aValidCartBuilder().userEntity(user)
                    .cartItems(new ArrayList<>(List.of(item))).build();
            user.setCartEntity(cart);

            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));
            when(cartRepository.save(cart)).thenReturn(cart);
            when(cartMapper.mapFromEntityToResponseDto(cart)).thenReturn(stubMappedResponse());

            service.decreaseQuantity(removeReq(product.getUuid(), 2), keycloakId);

            assertThat(cart.getCartItems()).isEmpty();
        }

        @Test
        @DisplayName("over-decrease (amount > current) clamps to 0 then auto-removes")
        void overDecrease_clampsAndRemoves() {
            final ProductEntity product = ProductEntityBuilder.aValidProduct();
            final UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).build();
            final CartItemEntity item = CartItemEntityBuilder.aValidCartItemBuilder().productEntity(product).quantity(3).build();
            final CartEntity cart = CartEntityBuilder.aValidCartBuilder().userEntity(user)
                    .cartItems(new ArrayList<>(List.of(item))).build();
            user.setCartEntity(cart);

            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));
            when(cartRepository.save(cart)).thenReturn(cart);
            when(cartMapper.mapFromEntityToResponseDto(cart)).thenReturn(stubMappedResponse());

            service.decreaseQuantity(removeReq(product.getUuid(), 99), keycloakId);

            assertThat(cart.getCartItems()).isEmpty();
        }

        @Test
        @DisplayName("empty cart -> CartIsEmptyException")
        void emptyCart_throws() {
            final UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).build();
            final CartEntity cart = CartEntityBuilder.aValidCartBuilder().userEntity(user)
                    .cartItems(new ArrayList<>()).build();
            user.setCartEntity(cart);
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> service.decreaseQuantity(removeReq(UUID.randomUUID(), 1), keycloakId))
                    .isInstanceOf(CartIsEmptyException.class);
        }

        @Test
        @DisplayName("user has no cart -> CartIsEmptyException")
        void noCart_throws() {
            final UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).cartEntity(null).build();
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> service.decreaseQuantity(removeReq(UUID.randomUUID(), 1), keycloakId))
                    .isInstanceOf(CartIsEmptyException.class);
        }

        @Test
        @DisplayName("product not in cart -> CartItemNotFoundException")
        void productNotInCart_throws() {
            final ProductEntity inCart = ProductEntityBuilder.aValidProduct();
            final UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).build();
            final CartItemEntity item = CartItemEntityBuilder.aValidCartItemBuilder().productEntity(inCart).build();
            final CartEntity cart = CartEntityBuilder.aValidCartBuilder().userEntity(user)
                    .cartItems(new ArrayList<>(List.of(item))).build();
            user.setCartEntity(cart);

            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> service.decreaseQuantity(removeReq(UUID.randomUUID(), 1), keycloakId))
                    .isInstanceOf(CartItemNotFoundException.class);
        }
    }

    // =================================================================
    @Nested
    @DisplayName("clearCart")
    class ClearCart {

        @Test
        @DisplayName("happy: clears items and saves cart")
        void happy_clearsItems() {
            final ProductEntity product = ProductEntityBuilder.aValidProduct();
            final UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).build();
            final CartItemEntity item = CartItemEntityBuilder.aValidCartItemBuilder().productEntity(product).build();
            final CartEntity cart = CartEntityBuilder.aValidCartBuilder().userEntity(user)
                    .cartItems(new ArrayList<>(List.of(item))).build();
            user.setCartEntity(cart);

            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));

            service.clearCart(keycloakId);

            assertThat(cart.getCartItems()).isEmpty();
            verify(cartRepository).save(cart);
        }

        @Test
        @DisplayName("empty cart no-op: clear() called but doesn't throw")
        void emptyCart_noOp() {
            final UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).build();
            final CartEntity cart = CartEntityBuilder.aValidCartBuilder().userEntity(user)
                    .cartItems(new ArrayList<>()).build();
            user.setCartEntity(cart);

            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));

            service.clearCart(keycloakId);
            verify(cartRepository).save(cart);
        }

        @Test
        @DisplayName("user with null cart: no save called")
        void nullCart_noSave() {
            final UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).cartEntity(null).build();
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));

            service.clearCart(keycloakId);
            verify(cartRepository, never()).save(any());
        }

        @Test
        @DisplayName("user missing -> UserNotFoundException")
        void userMissing_throws() {
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.clearCart(keycloakId)).isInstanceOf(UserNotFoundException.class);
            verify(cartRepository, never()).save(any());
        }
    }

    // =================================================================
    @Nested
    @DisplayName("CRUD admin: getAll / getByUUID / getByUUIDs / create / update / deleteByUUID(s)")
    class CrudAdmin {

        @Test
        @DisplayName("getAll delegates to repository.findAll and maps")
        void getAll_happy() {
            final List<CartEntity> all = List.of(CartEntityBuilder.aValidCart());
            final List<CartResponseDto> dtos = List.of(new CartResponseDto());
            when(cartRepository.findAll()).thenReturn(all);
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
        @DisplayName("getByUUIDs delegates to repository and maps collection")
        void getByUuids_happy() {
            final List<UUID> uuids = List.of(UUID.randomUUID());
            final List<CartEntity> entities = List.of(CartEntityBuilder.aValidCart());
            final List<CartResponseDto> dtos = List.of(new CartResponseDto());
            when(cartRepository.findAllByUuidIn(uuids)).thenReturn(entities);
            when(cartMapper.mapFromEntityToResponseDto((Collection<CartEntity>) entities))
                    .thenReturn((Collection<CartResponseDto>) (Collection<?>) dtos);

            Collection<CartResponseDto> result = service.getByUUIDs(uuids);
            assertThat(result).isEqualTo(dtos);
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
        @DisplayName("deleteByUUIDs delegates to repository.deleteAllByUuidIn")
        void deleteByUuids_delegates() {
            final List<UUID> uuids = List.of(UUID.randomUUID(), UUID.randomUUID());
            service.deleteByUUIDs(uuids);
            verify(cartRepository).deleteAllByUuidIn(uuids);
        }

        @Test
        @Disabled("BUG-026: F1 wave claimed update(...) was migrated to a new CartUpdateRequestDto. " +
                "Source verification: no CartUpdateRequestDto.java in src/main; signature is still " +
                "update(CartItemRemoveRequestDto). Re-enable when the signature is corrected and the " +
                "method updates an existing cart instead of routing through mapFromUpdateRequestToEntity.")
        @DisplayName("BUG-026: update should accept proper update DTO and modify existing cart")
        void update_shouldUseProperDto_disabled() {
            // expected behavior placeholder
        }

        @Test
        @DisplayName("BUG-026 PIN: update(CartItemRemoveRequestDto) currently round-trips via " +
                "mapFromUpdateRequestToEntity then save -> response (no fetch-then-merge)")
        void update_currentBehavior_PIN() {
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
        @Disabled("BUG-160: getByUUID should perform an ownership check (caller matches cart.userEntity). " +
                "F1 wave claimed UnauthorizedCartAccessException was added. Source verification: no such " +
                "exception exists. Re-enable once the service rejects access to other users' carts.")
        @DisplayName("BUG-160: getByUUID should reject access to a cart not owned by caller")
        void getByUuid_shouldEnforceOwnership_disabled() {
            // placeholder
        }

        @Test
        @DisplayName("BUG-160 PIN: getByUUID currently returns ANY cart by UUID with no caller check")
        void getByUuid_noOwnershipCheck_PIN() {
            final UUID uuid = UUID.randomUUID();
            final UserEntity otherUser = UserEntityBuilder.aValidUserBuilder().keycloakId("OTHER").build();
            final CartEntity cart = CartEntityBuilder.aValidCartBuilder().uuid(uuid).userEntity(otherUser).build();
            final CartResponseDto resp = new CartResponseDto();
            when(cartRepository.findByUuid(uuid)).thenReturn(Optional.of(cart));
            when(cartMapper.mapFromEntityToResponseDto(cart)).thenReturn(resp);

            // No keycloakId/Jwt parameter -> no possibility of caller check; pin current insecure shape
            assertThat(service.getByUUID(uuid)).isSameAs(resp);
        }

        @Test
        @Disabled("BUG-161: deleteByUUID should perform an ownership check before deletion. " +
                "F1 wave claimed UnauthorizedCartAccessException was added. Source verification: no such " +
                "exception exists; deleteByUUID has no caller arg. Re-enable once fixed.")
        @DisplayName("BUG-161: deleteByUUID should reject deletion of a cart not owned by caller")
        void deleteByUuid_shouldEnforceOwnership_disabled() {
            // placeholder
        }

        @Test
        @DisplayName("BUG-161 PIN: deleteByUUID(UUID) takes no caller identity -> trivial IDOR shape pinned")
        void deleteByUuid_noCallerArg_PIN() {
            final UUID uuid = UUID.randomUUID();
            service.deleteByUUID(uuid);
            verify(cartRepository).deleteByUuid(uuid); // happens regardless of who's calling
        }
    }
}
