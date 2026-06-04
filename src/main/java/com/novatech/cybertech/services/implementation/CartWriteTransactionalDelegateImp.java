package com.novatech.cybertech.services.implementation;

import com.github.f4b6a3.uuid.UuidCreator;
import com.novatech.cybertech.dto.request.cart.CartCreateRequestDto;
import com.novatech.cybertech.dto.request.cart.CartItemAddRequestDto;
import com.novatech.cybertech.dto.response.cart.CartResponseDto;
import com.novatech.cybertech.entities.CartEntity;
import com.novatech.cybertech.entities.CartItemEntity;
import com.novatech.cybertech.entities.ProductEntity;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.exceptions.ProductNotFoundException;
import com.novatech.cybertech.exceptions.UserNotFoundException;
import com.novatech.cybertech.mappers.entity.CartMapper;
import com.novatech.cybertech.repositories.CartRepository;
import com.novatech.cybertech.repositories.ProductRepository;
import com.novatech.cybertech.repositories.UserRepository;
import com.novatech.cybertech.services.core.CartCacheHelper;
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
 * Transactional inner half of the BUG-160 cart-add design. See
 * {@link CartWriteTransactionalDelegate} for the full "why a separate bean / why commit-before-unlock"
 * rationale.
 *
 * <p><b>Layer-3 removal note.</b> The previous implementation resolved the cart via
 * {@code cartRepository.findByOwnerKeycloakIdForUpdate(keycloakId)} — a {@code SELECT ... FOR UPDATE}
 * pessimistic row lock — as a second, DB-level line of defence on top of the Redis lock. That has
 * been dropped: the per-user Redis lock held by {@code CartServiceImp.addItemsToCart} is now the
 * sole serialisation point. The cart is resolved with a plain {@code user.getCartEntity()} lazy
 * load, exactly as the pre-BUG-160 code did. The trade-off is documented on
 * {@code CartServiceImp.addItemsToCart}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CartWriteTransactionalDelegateImp implements CartWriteTransactionalDelegate {

    private final CartMapper cartMapper;
    private final UserRepository userRepository;
    private final CartRepository cartRepository;
    private final CartCacheHelper cartCacheHelper;
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
                        .unitPrice(product.getPrice())
                        .productEntity(product)
                        .cart(cartEntity) // link child to parent
                        .uuid(UuidCreator.getTimeOrderedEpoch())
                        .build();
                cartEntity.getCartItems().add(newItem);
            }
        }

        final CartEntity savedCart = cartRepository.save(cartEntity);
        final CartResponseDto cartResponseDto = cartMapper.mapFromEntityToResponseDto(savedCart);
        cartCacheHelper.putWithJitter(keycloakId, cartResponseDto);

        return cartResponseDto;
    }
}
