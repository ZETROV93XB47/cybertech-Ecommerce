package com.novatech.cybertech.services.core;

import com.novatech.cybertech.dto.request.cart.CartCreateRequestDto;
import com.novatech.cybertech.dto.request.cart.CartItemRemoveRequestDto;
import com.novatech.cybertech.dto.response.cart.CartResponseDto;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

public interface CartService extends CrudBaseService<UUID, CartCreateRequestDto, CartItemRemoveRequestDto, CartResponseDto> {
    void clearCart(final String keycloakId);

    CartResponseDto getCart(final String keycloakId);

    CartResponseDto addItemsToCart(final CartCreateRequestDto productsToAdd, final String keycloakId);

    CartResponseDto removeItemFromCart(final UUID productUuid, final String keycloakId);

    CartResponseDto decreaseQuantity(final CartItemRemoveRequestDto cartItemRemoveRequestDto, final String keycloakId);

}
