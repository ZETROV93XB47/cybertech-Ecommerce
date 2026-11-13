package com.novatech.cybertech.fixtures.dto;

import com.novatech.cybertech.dto.request.cart.CartCreateRequestDto;
import com.novatech.cybertech.dto.request.cart.CartItemAddRequestDto;
import com.novatech.cybertech.dto.request.cart.CartItemRemoveRequestDto;
import com.novatech.cybertech.dto.response.cart.CartItemResponseDto;
import com.novatech.cybertech.dto.response.cart.CartResponseDto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Tiny DTO factories for the cart surface.
 */
public final class CartDtoFixtures {

    private CartDtoFixtures() {
    }

    public static CartCreateRequestDto aValidCartCreateRequest() {
        return aValidCartCreateRequestBuilder().build();
    }

    public static CartCreateRequestDto.CartCreateRequestDtoBuilder aValidCartCreateRequestBuilder() {
        return CartCreateRequestDto.builder()
                .cartItemAddRequestDtos(List.of(aValidCartItemAddRequest()));
    }

    public static CartItemAddRequestDto aValidCartItemAddRequest() {
        return aValidCartItemAddRequestBuilder().build();
    }

    public static CartItemAddRequestDto.CartItemAddRequestDtoBuilder aValidCartItemAddRequestBuilder() {
        return CartItemAddRequestDto.builder()
                .productUuid(UUID.randomUUID())
                .quantity(1);
    }

    public static CartItemRemoveRequestDto aValidCartItemRemoveRequest() {
        return aValidCartItemRemoveRequestBuilder().build();
    }

    public static CartItemRemoveRequestDto.CartItemRemoveRequestDtoBuilder aValidCartItemRemoveRequestBuilder() {
        return CartItemRemoveRequestDto.builder()
                .productUuid(UUID.randomUUID())
                .quantity(1);
    }

    public static CartResponseDto aSampleCartResponse() {
        return aSampleCartResponseBuilder().build();
    }

    public static CartResponseDto.CartResponseDtoBuilder aSampleCartResponseBuilder() {
        return CartResponseDto.builder()
                .cartUuid(UUID.randomUUID())
                .userUuid(UUID.randomUUID())
                .items(List.of(aSampleCartItemResponse()))
                .totalPrice(new BigDecimal("99.99"));
    }

    public static CartItemResponseDto aSampleCartItemResponse() {
        return aSampleCartItemResponseBuilder().build();
    }

    public static CartItemResponseDto.CartItemResponseDtoBuilder aSampleCartItemResponseBuilder() {
        return CartItemResponseDto.builder()
                .cartItemUuid(UUID.randomUUID())
                .productUuid(UUID.randomUUID())
                .productName("Sample Product")
                .quantity(1)
                .unitPrice(new BigDecimal("99.99"))
                .lineItemTotalPrice(new BigDecimal("99.99"));
    }
}
