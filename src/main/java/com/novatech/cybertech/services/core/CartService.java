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
 * Extends the generic {@link CrudBaseService} for the historical CRUD shape
 * ({@code getByUUID / create / update / deleteByUUID}). The generics use
 * {@link CartItemRemoveRequestDto} as the update argument purely to keep that
 * base contract — the new {@link #updateCart(UUID, CartUpdateRequestDto, String)}
 * overload below is the <em>correct</em> update entrypoint introduced for
 * BUG-026 (a new argument type + caller identity for BUG-161 ownership
 * enforcement). The inherited {@code update(CartItemRemoveRequestDto)} remains
 * untouched so the base CRUD contract stays wired.
 */
public interface CartService extends CrudBaseService<UUID, CartCreateRequestDto, CartItemRemoveRequestDto, CartResponseDto> {
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
     * BUG-026 / BUG-161 — Update an existing cart identified by UUID, after
     * verifying the caller owns it.
     * <p>
     * This is a <em>new</em> method rather than a modification of the inherited
     * {@code update(CartItemRemoveRequestDto)} so the generic signatures of
     * {@link CrudBaseService} — and every other implementation of it — stay
     * stable. Using the dedicated {@link CartUpdateRequestDto} makes the intent
     * ("replace cart items with this list") explicit.
     *
     * @param cartUuid   the cart to update.
     * @param dto        the new items payload.
     * @param keycloakId Keycloak subject of the caller — must match the cart
     *                   owner or an {@link UnauthorizedCartAccessException} is
     *                   thrown (BUG-161).
     * @return the updated cart DTO.
     * @throws UnauthorizedCartAccessException when the caller does not own the cart.
     */
    CartResponseDto updateCart(final UUID cartUuid, final CartUpdateRequestDto dto, final String keycloakId);

    /**
     * BUG-161 — Ownership-checked variant of {@link #getByUUID(UUID)}.
     * <p>
     * Loads the cart, verifies {@code cart.userEntity.keycloakId} matches the
     * caller, and returns the DTO. On a mismatch, throws
     * {@link UnauthorizedCartAccessException}. The single-arg {@link #getByUUID(UUID)}
     * is left in place to preserve the {@link CrudBaseService} contract.
     *
     * @param cartUuid   cart to read.
     * @param keycloakId Keycloak subject of the caller.
     * @return the cart DTO.
     * @throws UnauthorizedCartAccessException when the caller does not own the cart.
     */
    CartResponseDto getByUUID(final UUID cartUuid, final String keycloakId);

    /**
     * BUG-161 — Ownership-checked variant of {@link #deleteByUUID(UUID)}.
     *
     * @param cartUuid   cart to delete.
     * @param keycloakId Keycloak subject of the caller.
     * @throws UnauthorizedCartAccessException when the caller does not own the cart.
     */
    void deleteByUUID(final UUID cartUuid, final String keycloakId);
}
