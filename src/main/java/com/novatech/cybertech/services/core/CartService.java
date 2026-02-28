package com.novatech.cybertech.services.core;

import com.novatech.cybertech.dto.request.cart.CartCreateRequestDto;
import com.novatech.cybertech.dto.request.cart.CartItemRemoveRequestDto;
import com.novatech.cybertech.dto.response.cart.CartResponseDto;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

public interface CartService extends CrudBaseService<UUID, CartCreateRequestDto, CartItemRemoveRequestDto, CartResponseDto> {
    void clearCart(final Jwt jwt);

    CartResponseDto getCart(final Jwt jwt);

    CartResponseDto addItemsToCart(final CartCreateRequestDto productsToAdd, final Jwt jwt);

    CartResponseDto removeItemFromCart(final UUID productUuid, final Jwt jwt);

    CartResponseDto decreaseQuantity(final CartItemRemoveRequestDto cartItemRemoveRequestDto, final Jwt jwt);

}
