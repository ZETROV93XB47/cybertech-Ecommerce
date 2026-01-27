package com.novatech.cybertech.services.core;

import com.novatech.cybertech.dto.request.cart.CartCreateRequestDto;
import com.novatech.cybertech.dto.request.cart.CartUpdateRequestDto;
import com.novatech.cybertech.dto.response.cart.CartResponseDto;

import java.util.Map;
import java.util.UUID;

public interface CartService extends CrudBaseService<UUID, CartCreateRequestDto, CartUpdateRequestDto, CartResponseDto> {
    public void clearCart(final String keycloakId);

    public CartResponseDto getCart(String keycloakId);

    public CartResponseDto addToCart(final Map<UUID, Integer> productsToAdd, final String keycloakId);

    public CartResponseDto removeItemFromCart(final UUID productUuid, final String keycloakId);

}
