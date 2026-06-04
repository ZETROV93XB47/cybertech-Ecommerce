package com.novatech.cybertech.services.implementation.shopping;

import com.novatech.cybertech.dto.request.cart.CartCreateRequestDto;
import com.novatech.cybertech.dto.request.cart.CartItemAddRequestDto;
import com.novatech.cybertech.dto.response.cart.CartResponseDto;
import com.novatech.cybertech.entities.CartEntity;
import com.novatech.cybertech.entities.CartItemEntity;
import com.novatech.cybertech.entities.ProductEntity;
import com.novatech.cybertech.entities.UserEntity;
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
import com.novatech.cybertech.services.implementation.CartWriteTransactionalDelegateImp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mockito unit tests for {@link CartWriteTransactionalDelegateImp} — the transactional inner half of
 * the BUG-160 cart-add design.
 *
 * <p>These tests exercise the read-modify-write itself (cart creation, quantity merge, stock guard,
 * product/user lookup), in isolation from the Redis lock orchestration which lives in
 * {@code CartServiceImp.addItemsToCart} and is covered by {@code CartServiceImpTest}. The
 * {@code @Transactional} annotation is a no-op under plain Mockito, so the body runs verbatim.
 *
 * <p>Post layer-3 removal: the delegate resolves the cart via {@code user.getCartEntity()} only —
 * there is no longer a {@code SELECT ... FOR UPDATE} repository call, so no such stub is needed.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CartWriteTransactionalDelegateImp — add-items read-modify-write")
class CartWriteTransactionalDelegateTest {

    @Mock CartMapper cartMapper;
    @Mock UserRepository userRepository;
    @Mock CartRepository cartRepository;
    @Mock CartCacheHelper cartCacheHelper;
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

    @Test
    @DisplayName("single product into empty cart -> creates cart, adds item, saves, caches")
    void singleProduct_intoEmptyCart_savesAndCaches() {
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
        verify(cartCacheHelper).putWithJitter(keycloakId, resp);
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
    @DisplayName("product missing in repo lookup -> ProductNotFoundException, no save/cache")
    void productMissing_throws() {
        final UUID missingUuid = UUID.randomUUID();
        final UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).cartEntity(null).build();
        when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));
        when(productRepository.findAllByUuidIn(anyCollection())).thenReturn(List.of());

        assertThatThrownBy(() -> delegate.addItemsWithinTransaction(requestFor(missingUuid, 1), keycloakId))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining(missingUuid.toString());

        verify(cartRepository, never()).save(any());
        verify(cartCacheHelper, never()).putWithJitter(anyString(), any());
    }

    @Test
    @DisplayName("user missing -> UserNotFoundException, no save/cache")
    void userMissing_throws() {
        when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> delegate.addItemsWithinTransaction(requestFor(UUID.randomUUID(), 1), keycloakId))
                .isInstanceOf(UserNotFoundException.class);

        verify(cartRepository, never()).save(any());
        verify(cartCacheHelper, never()).putWithJitter(anyString(), any());
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
