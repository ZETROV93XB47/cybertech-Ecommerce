package com.novatech.cybertech.services.implementation.shopping;

import com.novatech.cybertech.dto.data.RecommendationSignalDto;
import com.novatech.cybertech.dto.request.cart.CartCreateRequestDto;
import com.novatech.cybertech.dto.request.cart.CartItemAddRequestDto;
import com.novatech.cybertech.dto.request.cart.CartItemRemoveRequestDto;
import com.novatech.cybertech.dto.request.cart.CartUpdateRequestDto;
import com.novatech.cybertech.entities.CartEntity;
import com.novatech.cybertech.entities.CartItemEntity;
import com.novatech.cybertech.entities.ProductEntity;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.entities.enums.UserEventType;
import com.novatech.cybertech.events.RecommendationSignalsEvent;
import com.novatech.cybertech.exceptions.NotEnoughStockException;
import com.novatech.cybertech.exceptions.ProductNotFoundException;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Recommendation signals published by every cart write path of {@link CartWriteTransactionalDelegateImp}
 * (written to MongoDB after commit by the listener). The cart mechanics themselves are covered by
 * {@link CartWriteTransactionalDelegateTest}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CartWriteTransactionalDelegateImp — recommendation signals")
class CartWriteRecommendationSignalsTest {

    @Mock CartMapper cartMapper;
    @Mock UserRepository userRepository;
    @Mock CartRepository cartRepository;
    @Mock ProductRepository productRepository;
    @Mock ApplicationEventPublisher eventPublisher;

    @InjectMocks CartWriteTransactionalDelegateImp delegate;

    private final String keycloakId = "kc-" + UUID.randomUUID();
    private final ProductEntity phone = ProductEntityBuilder.aValidProductBuilder().category("SMARTPHONE").stock(50).build();
    private final ProductEntity watch = ProductEntityBuilder.aValidProductBuilder().category("SMARTWATCH").stock(50).build();
    private UserEntity user;
    private CartEntity cart;

    @BeforeEach
    void setUp() {
        user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).build();
        cart = CartEntityBuilder.aValidCartBuilder().userEntity(user).cartItems(new ArrayList<>()).build();
        user.setCartEntity(cart);
        when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));
        when(cartRepository.findByUuid(cart.getUuid())).thenReturn(Optional.of(cart));
        when(productRepository.findAllByUuidIn(anyCollection())).thenReturn(List.of(phone, watch));
        when(cartRepository.save(any(CartEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private void lineOf(final ProductEntity product, final int quantity) {
        cart.getCartItems().add(CartItemEntityBuilder.aValidCartItemBuilder().cart(cart).productEntity(product).quantity(quantity).build());
    }

    private static CartItemAddRequestDto item(final ProductEntity product, final int quantity) {
        return CartItemAddRequestDto.builder().productUuid(product.getUuid()).quantity(quantity).build();
    }

    private List<RecommendationSignalDto> publishedSignals() {
        final ArgumentCaptor<RecommendationSignalsEvent> captor = ArgumentCaptor.forClass(RecommendationSignalsEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        return captor.getValue().getSignals();
    }

    @Test
    @DisplayName("POST /cart/add (also the guest-cart merge at login): ADD_TO_CART per product, for the caller")
    void addPublishesAddToCart() {
        lineOf(phone, 1);

        delegate.addItemsWithinTransaction(CartCreateRequestDto.builder()
                .cartItemAddRequestDtos(new ArrayList<>(List.of(item(phone, 1), item(watch, 2)))).build(), keycloakId);

        assertThat(publishedSignals())
                .extracting(RecommendationSignalDto::getEventType, RecommendationSignalDto::getProductUuid, RecommendationSignalDto::getUserId)
                .containsExactlyInAnyOrder(
                        tuple(UserEventType.ADD_TO_CART, phone.getUuid(), keycloakId),
                        tuple(UserEventType.ADD_TO_CART, watch.getUuid(), keycloakId));
    }

    @Test
    @DisplayName("refused add (NOT_ENOUGH_STOCK) publishes nothing")
    void refusedAddPublishesNothing() {
        final ProductEntity scarce = ProductEntityBuilder.aValidProductBuilder().stock(1).reservedStock(0).build();
        when(productRepository.findAllByUuidIn(anyCollection())).thenReturn(List.of(scarce));

        assertThatThrownBy(() -> delegate.addItemsWithinTransaction(CartCreateRequestDto.builder()
                .cartItemAddRequestDtos(new ArrayList<>(List.of(item(scarce, 5)))).build(), keycloakId))
                .isInstanceOf(NotEnoughStockException.class);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("unknown product on add publishes nothing")
    void unknownProductPublishesNothing() {
        when(productRepository.findAllByUuidIn(anyCollection())).thenReturn(List.of());

        assertThatThrownBy(() -> delegate.addItemsWithinTransaction(CartCreateRequestDto.builder()
                .cartItemAddRequestDtos(new ArrayList<>(List.of(item(phone, 1)))).build(), keycloakId))
                .isInstanceOf(ProductNotFoundException.class);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("PATCH /cart/update: increase + new + vanished -> ADD, ADD, REMOVE; decrease alone -> nothing")
    void updatePublishesDiff() {
        lineOf(phone, 1);
        final ProductEntity screen = ProductEntityBuilder.aValidProductBuilder().category("MONITOR").build();
        lineOf(screen, 1);

        delegate.updateCartWithinTransaction(cart.getUuid(), CartUpdateRequestDto.builder()
                .cartItemAddRequestDtos(new ArrayList<>(List.of(item(phone, 3), item(watch, 1)))).build(), keycloakId);

        assertThat(publishedSignals())
                .extracting(RecommendationSignalDto::getEventType, RecommendationSignalDto::getProductUuid)
                .containsExactlyInAnyOrder(
                        tuple(UserEventType.ADD_TO_CART, phone.getUuid()),
                        tuple(UserEventType.ADD_TO_CART, watch.getUuid()),
                        tuple(UserEventType.REMOVE_FROM_CART, screen.getUuid()));
    }

    @Test
    @DisplayName("PATCH /cart/update lowering a quantity without removing the product publishes nothing")
    void updateDecreaseOnlyPublishesNothing() {
        lineOf(phone, 3);

        delegate.updateCartWithinTransaction(cart.getUuid(), CartUpdateRequestDto.builder()
                .cartItemAddRequestDtos(new ArrayList<>(List.of(item(phone, 1)))).build(), keycloakId);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("removeItemFromCart -> REMOVE_FROM_CART")
    void removePublishesRemove() {
        lineOf(phone, 2);
        lineOf(watch, 1);

        delegate.removeItemWithinTransaction(watch.getUuid(), keycloakId);

        assertThat(publishedSignals()).extracting(RecommendationSignalDto::getEventType, RecommendationSignalDto::getProductUuid)
                .containsExactly(tuple(UserEventType.REMOVE_FROM_CART, watch.getUuid()));
    }

    @Test
    @DisplayName("decreaseQuantity down to zero -> REMOVE_FROM_CART; partial decrease -> nothing")
    void decrease() {
        lineOf(phone, 2);

        delegate.decreaseQuantityWithinTransaction(CartItemRemoveRequestDto.builder().productUuid(phone.getUuid()).quantity(1).build(), keycloakId);
        verify(eventPublisher, never()).publishEvent(any());

        delegate.decreaseQuantityWithinTransaction(CartItemRemoveRequestDto.builder().productUuid(phone.getUuid()).quantity(1).build(), keycloakId);
        assertThat(publishedSignals()).extracting(RecommendationSignalDto::getEventType).containsExactly(UserEventType.REMOVE_FROM_CART);
    }

    @Test
    @DisplayName("DELETE /cart/clear by the user -> one REMOVE_FROM_CART per line")
    void userClearPublishesOnePerLine() {
        lineOf(phone, 2);
        lineOf(watch, 1);

        delegate.clearCartWithinTransaction(keycloakId, true);

        assertThat(publishedSignals()).extracting(RecommendationSignalDto::getEventType, RecommendationSignalDto::getProductUuid)
                .containsExactlyInAnyOrder(
                        tuple(UserEventType.REMOVE_FROM_CART, phone.getUuid()),
                        tuple(UserEventType.REMOVE_FROM_CART, watch.getUuid()));
    }

    @Test
    @DisplayName("clear after placeOrder publishes nothing, even with lines left")
    void clearAfterOrderPublishesNothing() {
        lineOf(phone, 2);

        delegate.clearCartWithinTransaction(keycloakId, false);

        assertThat(cart.getCartItems()).isEmpty();
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("lines are built with CartItemEntity, the quantity in the signal source is the post-change one")
    void cartItemsAreEntities() {
        lineOf(phone, 1);
        final CartItemEntity first = cart.getCartItems().getFirst();

        delegate.addItemsWithinTransaction(CartCreateRequestDto.builder()
                .cartItemAddRequestDtos(new ArrayList<>(List.of(item(phone, 1)))).build(), keycloakId);

        assertThat(first.getQuantity()).isEqualTo(2);
        assertThat(publishedSignals()).extracting(RecommendationSignalDto::getEventType).containsExactly(UserEventType.ADD_TO_CART);
    }
}
