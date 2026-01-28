package com.novatech.cybertech.services.core;

import com.novatech.cybertech.dto.request.cart.CartCreateRequestDto;
import com.novatech.cybertech.dto.request.cart.CartItemRemoveRequestDto;
import com.novatech.cybertech.dto.response.cart.CartResponseDto;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

public interface CartService extends CrudBaseService<UUID, CartCreateRequestDto, CartItemRemoveRequestDto, CartResponseDto> {
    public void clearCart(final Jwt jwt);

    public CartResponseDto getCart(final Jwt jwt);

    public CartResponseDto addItemsToCart(final CartCreateRequestDto productsToAdd, final Jwt jwt);

    public CartResponseDto removeItemFromCart(final UUID productUuid, final Jwt jwt);

    public CartResponseDto decreaseQuantity(final CartItemRemoveRequestDto cartItemRemoveRequestDto, final Jwt jwt);

}
