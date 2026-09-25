package com.novatech.cybertech.services.implementation.shopping;

import com.novatech.cybertech.dto.request.cart.CartCreateRequestDto;
import com.novatech.cybertech.dto.request.cart.CartItemAddRequestDto;
import com.novatech.cybertech.dto.request.cart.CartItemRemoveRequestDto;
import com.novatech.cybertech.dto.request.cart.CartUpdateRequestDto;
import com.novatech.cybertech.dto.response.cart.CartResponseDto;
import com.novatech.cybertech.entities.CartEntity;
import com.novatech.cybertech.entities.CartItemEntity;
import com.novatech.cybertech.entities.ProductEntity;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.exceptions.CannotRemoveItemFromEmptyCartException;
import com.novatech.cybertech.exceptions.CartIsEmptyException;
import com.novatech.cybertech.exceptions.CartItemNotFoundException;
import com.novatech.cybertech.exceptions.CartNotFoundException;
import com.novatech.cybertech.exceptions.NotEnoughStockException;
import com.novatech.cybertech.exceptions.ProductNotFoundException;
import com.novatech.cybertech.exceptions.UnauthorizedCartAccessException;
import com.novatech.cybertech.exceptions.UserNotFoundException;
import com.novatech.cybertech.fixtures.builders.CartEntityBuilder;
import com.novatech.cybertech.fixtures.builders.CartItemEntityBuilder;
import com.novatech.cybertech.fixtures.builders.ProductEntityBuilder;
import com.novatech.cybertech.fixtures.builders.UserEntityBuilder;
import com.novatech.cybertech.mappers.entity.CartMapper;
import com.novatech.cybertech.repositories.CartRepository;
import com.novatech.cybertech.repositories.ProductRepository;
import com.novatech.cybertech.repositories.UserRepository;
import com.novatech.cybertech.services.implementation.CartWriteTransactionalDelegateImp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mockito unit tests for {@link CartWriteTransactionalDelegateImp} — the transactional inner half of
 * every cart write path (add / remove / decrease / clear / update).
 *
 * <p>These tests exercise the read-modify-write itself (cart creation, quantity merge, stock guard,
 * ownership check, product/user lookup), in isolation from the Redis lock orchestration and the
 * cache write, both of which live in {@code CartServiceImp} and are covered by
 * {@code CartServiceImpTest}. The {@code @Transactional} annotation is a no-op under plain Mockito,
 * so the body runs verbatim.
 *
 * <p><b>No cache interaction here on purpose:</b> the cache write moved to the caller
 * ({@code CartServiceImp}), once each {@code *WithinTransaction} call has returned — i.e. once its
 * transaction has actually committed. Writing the cache from inside this bean would put it before
 * that commit. See {@code CartWriteTransactionalDelegate} javadoc for the full rationale.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CartWriteTransactionalDelegateImp")
class CartWriteTransactionalDelegateTest {

    @Mock CartMapper cartMapper;
    @Mock UserRepository userRepository;
    @Mock CartRepository cartRepository;
    @Mock ProductRepository productRepository;

    @InjectMocks CartWriteTransactionalDelegateImp delegate;

    String keycloakId;

    @BeforeEach
    void setUp() {
        keycloakId = "kc-" + UUID.randomUUID();
    }

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

    // =================================================================
    @Nested
    @DisplayName("addItemsWithinTransaction")
    class AddItems {

        @Test
        @DisplayName("single product into empty cart -> creates cart, adds item, saves")
        void singleProduct_intoEmptyCart_saves() {
            final ProductEntity product = ProductEntityBuilder.aValidProductBuilder().stock(10).reservedStock(0).build();
            final UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).cartEntity(null).build();
            final CartCreateRequestDto req = requestFor(product.getUuid(), 2);

            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));
            when(productRepository.findAllByUuidIn(anyCollection())).thenReturn(List.of(product));
            when(cartRepository.save(any(CartEntity.class))).thenAnswer(inv -> inv.getArgument(0));
            final CartResponseDto resp = stubMappedResponse();
            when(cartMapper.mapFromEntityToResponseDto(any(CartEntity.class))).thenReturn(resp);

            final CartResponseDto result = delegate.addItemsWithinTransaction(req, keycloakId);

            assertThat(result).isSameAs(resp);
            final ArgumentCaptor<CartEntity> savedCart = ArgumentCaptor.forClass(CartEntity.class);
            verify(cartRepository).save(savedCart.capture());
            assertThat(savedCart.getValue().getCartItems()).hasSize(1);
            assertThat(savedCart.getValue().getCartItems().get(0).getQuantity()).isEqualTo(2);
        }

        @Test
        @DisplayName("multi-product saves all items in one cart")
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

            delegate.addItemsWithinTransaction(req, keycloakId);

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

            delegate.addItemsWithinTransaction(requestFor(product.getUuid(), 3), keycloakId);

            assertThat(cart.getCartItems()).hasSize(1);
            assertThat(cart.getCartItems().get(0).getQuantity()).isEqualTo(5);
        }

        @Test
        @DisplayName("product missing in repo lookup -> ProductNotFoundException, no save")
        void productMissing_throws() {
            final UUID missingUuid = UUID.randomUUID();
            final UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).cartEntity(null).build();
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));
            when(productRepository.findAllByUuidIn(anyCollection())).thenReturn(List.of());

            assertThatThrownBy(() -> delegate.addItemsWithinTransaction(requestFor(missingUuid, 1), keycloakId))
                    .isInstanceOf(ProductNotFoundException.class)
                    .hasMessageContaining(missingUuid.toString());

            verify(cartRepository, never()).save(any());
        }

        @Test
        @DisplayName("user missing -> UserNotFoundException, no save")
        void userMissing_throws() {
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> delegate.addItemsWithinTransaction(requestFor(UUID.randomUUID(), 1), keycloakId))
                    .isInstanceOf(UserNotFoundException.class);

            verify(cartRepository, never()).save(any());
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

            delegate.addItemsWithinTransaction(requestFor(product.getUuid(), 1), keycloakId);

            final ArgumentCaptor<CartEntity> captor = ArgumentCaptor.forClass(CartEntity.class);
            verify(cartRepository).save(captor.capture());
            assertThat(captor.getValue().getUuid()).isNotNull();
            assertThat(captor.getValue().getUserEntity()).isSameAs(user);
        }

        @Test
        @DisplayName("not enough stock -> NotEnoughStockException, no save")
        void notEnoughStock_throws() {
            final ProductEntity product = ProductEntityBuilder.aValidProductBuilder().stock(2).reservedStock(0).build();
            final UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).cartEntity(null).build();
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));
            when(productRepository.findAllByUuidIn(anyCollection())).thenReturn(List.of(product));

            assertThatThrownBy(() -> delegate.addItemsWithinTransaction(requestFor(product.getUuid(), 5), keycloakId))
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

            assertThatThrownBy(() -> delegate.addItemsWithinTransaction(requestFor(product.getUuid(), 3), keycloakId))
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
            delegate.addItemsWithinTransaction(requestFor(product.getUuid(), 3), keycloakId);

            assertThat(cart.getCartItems()).hasSize(1);
            assertThat(cart.getCartItems().get(0).getQuantity()).isEqualTo(5);
        }
    }

    // =================================================================
    @Nested
    @DisplayName("removeItemWithinTransaction")
    class RemoveItem {

        @Test
        @DisplayName("happy: removes item, saves cart")
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

            final CartResponseDto result = delegate.removeItemWithinTransaction(product.getUuid(), keycloakId);

            assertThat(result).isSameAs(resp);
            assertThat(cart.getCartItems()).isEmpty();
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

            delegate.removeItemWithinTransaction(product.getUuid(), keycloakId);
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

            assertThatThrownBy(() -> delegate.removeItemWithinTransaction(UUID.randomUUID(), keycloakId))
                    .isInstanceOf(CannotRemoveItemFromEmptyCartException.class);
            verify(cartRepository, never()).save(any());
        }

        @Test
        @DisplayName("user has no cart -> CannotRemoveItemFromEmptyCartException")
        void noCart_throws() {
            final UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).cartEntity(null).build();
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> delegate.removeItemWithinTransaction(UUID.randomUUID(), keycloakId))
                    .isInstanceOf(CannotRemoveItemFromEmptyCartException.class);
        }

        @Test
        @DisplayName("user missing -> UserNotFoundException")
        void userMissing_throws() {
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> delegate.removeItemWithinTransaction(UUID.randomUUID(), keycloakId))
                    .isInstanceOf(UserNotFoundException.class);
        }
    }

    // =================================================================
    @Nested
    @DisplayName("decreaseQuantityWithinTransaction")
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

            delegate.decreaseQuantityWithinTransaction(removeReq(product.getUuid(), 2), keycloakId);

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

            delegate.decreaseQuantityWithinTransaction(removeReq(product.getUuid(), 2), keycloakId);

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

            delegate.decreaseQuantityWithinTransaction(removeReq(product.getUuid(), 99), keycloakId);

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

            assertThatThrownBy(() -> delegate.decreaseQuantityWithinTransaction(removeReq(UUID.randomUUID(), 1), keycloakId))
                    .isInstanceOf(CartIsEmptyException.class);
        }

        @Test
        @DisplayName("user has no cart -> CartIsEmptyException")
        void noCart_throws() {
            final UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).cartEntity(null).build();
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> delegate.decreaseQuantityWithinTransaction(removeReq(UUID.randomUUID(), 1), keycloakId))
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

            assertThatThrownBy(() -> delegate.decreaseQuantityWithinTransaction(removeReq(UUID.randomUUID(), 1), keycloakId))
                    .isInstanceOf(CartItemNotFoundException.class);
        }
    }

    // =================================================================
    @Nested
    @DisplayName("clearCartWithinTransaction")
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
            when(cartRepository.save(cart)).thenReturn(cart);
            when(cartMapper.mapFromEntityToResponseDto(cart)).thenReturn(stubMappedResponse());

            final CartResponseDto result = delegate.clearCartWithinTransaction(keycloakId);

            assertThat(result).isNotNull();
            assertThat(cart.getCartItems()).isEmpty();
            verify(cartRepository).save(cart);
        }

        @Test
        @DisplayName("already-empty cart: still saved, no-op clear")
        void emptyCart_noOp() {
            final UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).build();
            final CartEntity cart = CartEntityBuilder.aValidCartBuilder().userEntity(user)
                    .cartItems(new ArrayList<>()).build();
            user.setCartEntity(cart);

            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));
            when(cartRepository.save(cart)).thenReturn(cart);
            when(cartMapper.mapFromEntityToResponseDto(cart)).thenReturn(stubMappedResponse());

            delegate.clearCartWithinTransaction(keycloakId);
            verify(cartRepository).save(cart);
        }

        @Test
        @DisplayName("user with null cart: returns null, no save")
        void nullCart_returnsNullNoSave() {
            final UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).cartEntity(null).build();
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));

            final CartResponseDto result = delegate.clearCartWithinTransaction(keycloakId);

            assertThat(result).isNull();
            verify(cartRepository, never()).save(any());
        }

        @Test
        @DisplayName("user missing -> UserNotFoundException")
        void userMissing_throws() {
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> delegate.clearCartWithinTransaction(keycloakId)).isInstanceOf(UserNotFoundException.class);
            verify(cartRepository, never()).save(any());
        }
    }

    // =================================================================
    @Nested
    @DisplayName("updateCartWithinTransaction")
    class UpdateCart {

        @Test
        @DisplayName("replaces the cart's items and returns the updated DTO")
        void shouldReplaceItems() {
            final UUID cartUuid = UUID.randomUUID();
            final ProductEntity product = ProductEntityBuilder.aValidProductBuilder().build();
            final UserEntity owner = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).build();
            final CartEntity existingCart = CartEntityBuilder.aValidCartBuilder()
                    .uuid(cartUuid)
                    .userEntity(owner)
                    .cartItems(new ArrayList<>())
                    .build();
            final CartUpdateRequestDto dto = CartUpdateRequestDto.builder()
                    .cartItemAddRequestDtos(new ArrayList<>(List.of(
                            CartItemAddRequestDto.builder().productUuid(product.getUuid()).quantity(4).build())))
                    .build();
            final CartResponseDto mapped = stubMappedResponse();

            when(cartRepository.findByUuid(cartUuid)).thenReturn(Optional.of(existingCart));
            when(productRepository.findAllByUuidIn(anyCollection())).thenReturn(List.of(product));
            when(cartRepository.save(existingCart)).thenReturn(existingCart);
            when(cartMapper.mapFromEntityToResponseDto(existingCart)).thenReturn(mapped);

            final CartResponseDto result = delegate.updateCartWithinTransaction(cartUuid, dto, keycloakId);

            assertThat(result).isSameAs(mapped);
            assertThat(existingCart.getCartItems()).hasSize(1);
            assertThat(existingCart.getCartItems().get(0).getQuantity()).isEqualTo(4);
        }

        @Test
        @DisplayName("rejects when requested qty exceeds available stock")
        void shouldFailWhenRequestedQtyExceedsAvailableStock() {
            final UUID cartUuid = UUID.randomUUID();
            final ProductEntity product = ProductEntityBuilder.aValidProductBuilder()
                    .stock(2).reservedStock(0).build();
            final UserEntity owner = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).build();
            final CartEntity existingCart = CartEntityBuilder.aValidCartBuilder()
                    .uuid(cartUuid).userEntity(owner).cartItems(new ArrayList<>()).build();
            final CartUpdateRequestDto dto = CartUpdateRequestDto.builder()
                    .cartItemAddRequestDtos(new ArrayList<>(List.of(
                            CartItemAddRequestDto.builder().productUuid(product.getUuid()).quantity(5).build())))
                    .build();

            when(cartRepository.findByUuid(cartUuid)).thenReturn(Optional.of(existingCart));
            when(productRepository.findAllByUuidIn(anyCollection())).thenReturn(List.of(product));

            assertThatThrownBy(() -> delegate.updateCartWithinTransaction(cartUuid, dto, keycloakId))
                    .isInstanceOf(NotEnoughStockException.class);

            verify(cartRepository, never()).save(any());
        }

        @Test
        @DisplayName("rejects when requested qty exceeds (stock - reservedStock)")
        void shouldFailWhenRequestedQtyExceedsStockMinusReservedStock() {
            // stock=10, reservedStock=8, requested=5 -> 8+5=13 > 10 -> must throw.
            final UUID cartUuid = UUID.randomUUID();
            final ProductEntity product = ProductEntityBuilder.aValidProductBuilder()
                    .stock(10).reservedStock(8).build();
            final UserEntity owner = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).build();
            final CartEntity existingCart = CartEntityBuilder.aValidCartBuilder()
                    .uuid(cartUuid).userEntity(owner).cartItems(new ArrayList<>()).build();
            final CartUpdateRequestDto dto = CartUpdateRequestDto.builder()
                    .cartItemAddRequestDtos(new ArrayList<>(List.of(
                            CartItemAddRequestDto.builder().productUuid(product.getUuid()).quantity(5).build())))
                    .build();

            when(cartRepository.findByUuid(cartUuid)).thenReturn(Optional.of(existingCart));
            when(productRepository.findAllByUuidIn(anyCollection())).thenReturn(List.of(product));

            assertThatThrownBy(() -> delegate.updateCartWithinTransaction(cartUuid, dto, keycloakId))
                    .isInstanceOf(NotEnoughStockException.class);

            verify(cartRepository, never()).save(any());
        }

        @Test
        @DisplayName("succeeds when requested qty equals available stock (boundary)")
        void shouldSucceedWhenRequestedQtyEqualsAvailableStock() {
            // stock=5, reserved=2, requested=3 -> 2+3=5 == stock -> ok.
            final UUID cartUuid = UUID.randomUUID();
            final ProductEntity product = ProductEntityBuilder.aValidProductBuilder()
                    .stock(5).reservedStock(2).build();
            final UserEntity owner = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).build();
            final CartEntity existingCart = CartEntityBuilder.aValidCartBuilder()
                    .uuid(cartUuid).userEntity(owner).cartItems(new ArrayList<>()).build();
            final CartUpdateRequestDto dto = CartUpdateRequestDto.builder()
                    .cartItemAddRequestDtos(new ArrayList<>(List.of(
                            CartItemAddRequestDto.builder().productUuid(product.getUuid()).quantity(3).build())))
                    .build();
            final CartResponseDto mapped = stubMappedResponse();

            when(cartRepository.findByUuid(cartUuid)).thenReturn(Optional.of(existingCart));
            when(productRepository.findAllByUuidIn(anyCollection())).thenReturn(List.of(product));
            when(cartRepository.save(existingCart)).thenReturn(existingCart);
            when(cartMapper.mapFromEntityToResponseDto(existingCart)).thenReturn(mapped);

            final CartResponseDto result = delegate.updateCartWithinTransaction(cartUuid, dto, keycloakId);

            assertThat(result).isSameAs(mapped);
            assertThat(existingCart.getCartItems()).hasSize(1);
            assertThat(existingCart.getCartItems().get(0).getQuantity()).isEqualTo(3);
        }

        @Test
        @DisplayName("fail-fast — when one line fails stock check, cart NOT mutated at all")
        void shouldNotMutateOtherItemsWhenOneFails() {
            // Two lines: line 1 ok, line 2 over stock. Existing cart has a pre-existing
            // item that must remain in place because the validation must happen BEFORE
            // the cart is cleared/re-populated.
            final UUID cartUuid = UUID.randomUUID();
            final ProductEntity okProduct = ProductEntityBuilder.aValidProductBuilder()
                    .stock(10).reservedStock(0).build();
            final ProductEntity overStockProduct = ProductEntityBuilder.aValidProductBuilder()
                    .stock(2).reservedStock(0).build();
            final UserEntity owner = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).build();
            final ProductEntity preExistingProduct = ProductEntityBuilder.aValidProduct();
            final CartItemEntity preExistingItem = CartItemEntityBuilder.aValidCartItemBuilder()
                    .productEntity(preExistingProduct).quantity(7).build();
            final CartEntity existingCart = CartEntityBuilder.aValidCartBuilder()
                    .uuid(cartUuid).userEntity(owner)
                    .cartItems(new ArrayList<>(List.of(preExistingItem))).build();

            final CartUpdateRequestDto dto = CartUpdateRequestDto.builder()
                    .cartItemAddRequestDtos(new ArrayList<>(List.of(
                            CartItemAddRequestDto.builder().productUuid(okProduct.getUuid()).quantity(2).build(),
                            CartItemAddRequestDto.builder().productUuid(overStockProduct.getUuid()).quantity(99).build())))
                    .build();

            when(cartRepository.findByUuid(cartUuid)).thenReturn(Optional.of(existingCart));
            when(productRepository.findAllByUuidIn(anyCollection()))
                    .thenReturn(List.of(okProduct, overStockProduct));

            assertThatThrownBy(() -> delegate.updateCartWithinTransaction(cartUuid, dto, keycloakId))
                    .isInstanceOf(NotEnoughStockException.class);

            // The cart must NOT have been mutated: pre-existing item still there with original qty.
            assertThat(existingCart.getCartItems()).hasSize(1);
            assertThat(existingCart.getCartItems().get(0).getQuantity()).isEqualTo(7);
            assertThat(existingCart.getCartItems().get(0).getProductEntity().getUuid())
                    .isEqualTo(preExistingProduct.getUuid());
            verify(cartRepository, never()).save(any());
        }

        @Test
        @DisplayName("rejects caller that doesn't own the cart")
        void rejectsNonOwner() {
            final UUID cartUuid = UUID.randomUUID();
            final UserEntity otherOwner = UserEntityBuilder.aValidUserBuilder().keycloakId("OTHER_USER").build();
            final CartEntity cart = CartEntityBuilder.aValidCartBuilder()
                    .uuid(cartUuid).userEntity(otherOwner).cartItems(new ArrayList<>()).build();
            when(cartRepository.findByUuid(cartUuid)).thenReturn(Optional.of(cart));

            final CartUpdateRequestDto dto = CartUpdateRequestDto.builder()
                    .cartItemAddRequestDtos(new ArrayList<>()).build();

            assertThatThrownBy(() -> delegate.updateCartWithinTransaction(cartUuid, dto, keycloakId))
                    .isInstanceOf(UnauthorizedCartAccessException.class);
            verify(cartRepository, never()).save(any());
        }

        @Test
        @DisplayName("missing cart -> CartNotFoundException")
        void missingCart_throws() {
            final UUID cartUuid = UUID.randomUUID();
            when(cartRepository.findByUuid(cartUuid)).thenReturn(Optional.empty());

            final CartUpdateRequestDto dto = CartUpdateRequestDto.builder()
                    .cartItemAddRequestDtos(new ArrayList<>()).build();

            assertThatThrownBy(() -> delegate.updateCartWithinTransaction(cartUuid, dto, keycloakId))
                    .isInstanceOf(CartNotFoundException.class);
        }
    }
}
