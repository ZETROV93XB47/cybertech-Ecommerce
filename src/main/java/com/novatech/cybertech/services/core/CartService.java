package com.novatech.cybertech.services.core;

import com.novatech.cybertech.dto.request.cart.CartCreateRequestDto;
import com.novatech.cybertech.dto.request.cart.CartItemRemoveRequestDto;
import com.novatech.cybertech.dto.request.cart.CartUpdateRequestDto;
import com.novatech.cybertech.dto.response.cart.CartResponseDto;
import com.novatech.cybertech.exceptions.UnauthorizedCartAccessException;

import java.util.UUID;

/**
 * Cart domain service contract.
 * <p>
 * Deliberately NOT a {@link CrudBaseService}: a cart is an implicit, per-user resource, not a
 * CRUD entity. It is created on the first {@link #addItemsToCart} call, emptied by
 * {@link #clearCart} (also done automatically once an order is placed) and removed by cascade
 * with its owner — so a generic {@code create} / {@code getByUUID} / {@code deleteByUUID} has no
 * real use case. Every operation is keyed on the caller's Keycloak subject.
 */
public interface CartService {
    /**
     * Remove every line-item from the authenticated user's cart.
     *
     * @param keycloakId Keycloak subject of the calling user.
     */
    void clearCart(final String keycloakId);

    /**
     * Retrieve the cart owned by the authenticated user (cache-first, DB fallback).
     *
     * @param keycloakId Keycloak subject of the calling user.
     * @return the user's cart DTO (possibly empty when the user has no cart yet).
     */
    CartResponseDto getCart(final String keycloakId);

    /**
     * Add one or more items to the authenticated user's cart.
     *
     * @param productsToAdd items to add; quantities must be {@code >= 1}.
     * @param keycloakId    Keycloak subject of the calling user.
     * @return the updated cart DTO.
     */
    CartResponseDto addItemsToCart(final CartCreateRequestDto productsToAdd, final String keycloakId);

    /**
     * Remove a single product (whatever its quantity) from the user's cart.
     *
     * @param productUuid product to remove entirely.
     * @param keycloakId  Keycloak subject of the calling user.
     * @return the updated cart DTO.
     */
    CartResponseDto removeItemFromCart(final UUID productUuid, final String keycloakId);

    /**
     * Decrease the quantity of a single cart line.
     *
     * @param cartItemRemoveRequestDto product UUID + quantity to subtract.
     * @param keycloakId               Keycloak subject of the calling user.
     * @return the updated cart DTO.
     */
    CartResponseDto decreaseQuantity(final CartItemRemoveRequestDto cartItemRemoveRequestDto, final String keycloakId);

    /**
     * Update an existing cart identified by UUID, after verifying the caller
     * owns it.
     * @param cartUuid   the cart to update.
     * @param dto        the new items payload.
     * @param keycloakId Keycloak subject of the caller — must match the cart
     *                   owner or an {@link UnauthorizedCartAccessException} is
     *                   thrown.
     * @return the updated cart DTO.
     * @throws UnauthorizedCartAccessException when the caller does not own the cart.
     */
    CartResponseDto updateCart(final UUID cartUuid, final CartUpdateRequestDto dto, final String keycloakId);

}
