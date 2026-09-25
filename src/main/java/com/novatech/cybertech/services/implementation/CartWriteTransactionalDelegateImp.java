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
import com.novatech.cybertech.exceptions.CannotRemoveItemFromEmptyCartException;
import com.novatech.cybertech.exceptions.CartIsEmptyException;
import com.novatech.cybertech.exceptions.CartItemNotFoundException;
import com.novatech.cybertech.exceptions.CartNotFoundException;
import com.novatech.cybertech.exceptions.ProductNotFoundException;
import com.novatech.cybertech.exceptions.UnauthorizedCartAccessException;
import com.novatech.cybertech.exceptions.UserNotFoundException;
import com.novatech.cybertech.mappers.entity.CartMapper;
import com.novatech.cybertech.repositories.CartRepository;
import com.novatech.cybertech.repositories.ProductRepository;
import com.novatech.cybertech.repositories.UserRepository;
import com.novatech.cybertech.services.core.CartWriteTransactionalDelegate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Transactional inner half of the cart-add design. See
 * {@link CartWriteTransactionalDelegate} for the full "why a separate bean / why commit-before-unlock"
 * rationale.
 *
 * <p><b>Layer-3 removal note.</b> The previous implementation resolved the cart via
 * {@code cartRepository.findByOwnerKeycloakIdForUpdate(keycloakId)} — a {@code SELECT ... FOR UPDATE}
 * pessimistic row lock — as a second, DB-level line of defence on top of the Redis lock. That has
 * been dropped: the per-user Redis lock held by {@code CartServiceImp.addItemsToCart} is now the
 * sole serialisation point. The cart is resolved with a plain {@code user.getCartEntity()} lazy
 * load, exactly as the prior code did. The trade-off is documented on
 * {@code CartServiceImp.addItemsToCart}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CartWriteTransactionalDelegateImp implements CartWriteTransactionalDelegate {

    private final CartMapper cartMapper;
    private final UserRepository userRepository;
    private final CartRepository cartRepository;
    private final ProductRepository productRepository;

    @Override
    @Transactional
    public CartResponseDto addItemsWithinTransaction(final CartCreateRequestDto dto, final String keycloakId) {
        final Map<UUID, Integer> productsToAdd = dto.getCartItemAddRequestDtos().stream().collect(Collectors.toMap(CartItemAddRequestDto::getProductUuid, CartItemAddRequestDto::getQuantity));
        final UserEntity user = userRepository.findByKeycloakId(keycloakId).orElseThrow(() -> new UserNotFoundException("User not found"));

        // Single round-trip to load every requested product.
        final List<ProductEntity> products = productRepository.findAllByUuidIn(productsToAdd.keySet());
        final Map<UUID, ProductEntity> productMap = products.stream().collect(Collectors.toMap(ProductEntity::getUuid, p -> p));

        // Layer 3 removed — resolve the cart by lazy navigation. The per-user Redis lock (held by
        // the caller) already serialises the read-modify-write, so a SELECT ... FOR UPDATE is no
        // longer needed. A brand-new user has no cart yet -> getCartEntity() is null and we create one.
        CartEntity cartEntity = user.getCartEntity();

        if (cartEntity == null) {
            cartEntity = CartEntity.builder()
                    .userEntity(user)
                    .cartItems(new ArrayList<>())
                    .uuid(UuidCreator.getTimeOrderedEpoch())
                    .build();
        }

        for (final Map.Entry<UUID, Integer> entry : productsToAdd.entrySet()) {
            final UUID productUuid = entry.getKey();
            final Integer quantity = entry.getValue();
            final ProductEntity product = productMap.get(productUuid);
            if (product == null) {
                throw new ProductNotFoundException("No product with the UUID : " + productUuid + " found");
            }

            final CartEntity currentCart = cartEntity;
            final Optional<CartItemEntity> existingItem = currentCart.getCartItems().stream()
                    .filter(item -> item.getProductEntity().getUuid().equals(productUuid))
                    .findFirst();

            final int newQuantity = existingItem.map(item -> item.getQuantity() + quantity).orElse(quantity);

            // Advisory stock guard (reservedStock + total requested vs total stock).
            CartStockValidator.validateStockAvailability(product, newQuantity);

            if (existingItem.isPresent()) {
                existingItem.get().increaseQuantity(quantity);
            } else {
                final CartItemEntity newItem = CartItemEntity.builder()
                        .quantity(quantity)
                        .productEntity(product)
                        .cart(cartEntity) // link child to parent
                        .uuid(UuidCreator.getTimeOrderedEpoch())
                        .build();
                cartEntity.getCartItems().add(newItem);
            }
        }

        final CartEntity savedCart = cartRepository.save(cartEntity);
        return cartMapper.mapFromEntityToResponseDto(savedCart);
    }

    @Override
    @Transactional
    public CartResponseDto removeItemWithinTransaction(final UUID productUuid, final String keycloakId) {
        final UserEntity user = userRepository.findByKeycloakId(keycloakId).orElseThrow(() -> new UserNotFoundException("User not found"));
        final CartEntity cart = user.getCartEntity();

        if (cart != null && cart.getCartItems() != null) {
            final boolean removed = cart.getCartItems().removeIf(item -> item.getProductEntity().getUuid().equals(productUuid));

            if (removed) {
                final CartEntity savedCart = cartRepository.save(cart);
                return cartMapper.mapFromEntityToResponseDto(savedCart);
            }
        }
        throw new CannotRemoveItemFromEmptyCartException("Cannot remove item already absent from cart.");
    }

    @Override
    @Transactional
    public CartResponseDto decreaseQuantityWithinTransaction(final CartItemRemoveRequestDto cartItemRemoveRequestDto, final String keycloakId) {
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

        if (updateResult <= 0) {
            cart.getCartItems().remove(cartItemToDecrease);
        }

        final CartEntity savedCart = cartRepository.save(cart);
        return cartMapper.mapFromEntityToResponseDto(savedCart);
    }

    @Override
    @Transactional
    public CartResponseDto clearCartWithinTransaction(final String keycloakId) {
        final UserEntity user = userRepository.findByKeycloakId(keycloakId).orElseThrow(() -> new UserNotFoundException("User not found"));
        final CartEntity cart = user.getCartEntity();

        if (cart == null || cart.getCartItems() == null) {
            return null;
        }

        cart.getCartItems().clear();
        final CartEntity savedCart = cartRepository.save(cart);
        return cartMapper.mapFromEntityToResponseDto(savedCart);
    }

    @Override
    @Transactional
    public CartResponseDto updateCartWithinTransaction(final UUID cartUuid, final CartUpdateRequestDto dto, final String keycloakId) {
        final CartEntity cart = cartRepository.findByUuid(cartUuid)
                .orElseThrow(() -> new CartNotFoundException("No cart with the UUID : " + cartUuid + " found"));
        assertCallerOwnsCart(cart, cartUuid, keycloakId);

        final List<CartItemAddRequestDto> items = dto.getCartItemAddRequestDtos();

        // Validate every line (product existence + stock availability) BEFORE mutating the cart —
        // see CartServiceImp's former updateCart javadoc for the fail-fast rationale.
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
        return cartMapper.mapFromEntityToResponseDto(saved);
    }

    /**
     * Ownership guard shared by {@link #updateCartWithinTransaction}. Mirrors
     * {@code CartServiceImp#assertCallerOwnsCart}.
     */
    private void assertCallerOwnsCart(final CartEntity cart, final UUID cartUuid, final String keycloakId) {
        final UserEntity owner = cart.getUserEntity();
        if (owner == null || owner.getKeycloakId() == null || !owner.getKeycloakId().equals(keycloakId)) {
            log.warn("Unauthorized cart access attempt: caller {} on cart {}", keycloakId, cartUuid);
            throw new UnauthorizedCartAccessException("Caller does not own cart " + cartUuid);
        }
    }
}
