package com.novatech.cybertech.fixtures.dto;

import com.novatech.cybertech.dto.request.wishlist.WishlistCreateRequestDto;
import com.novatech.cybertech.dto.request.wishlist.WishlistUpdateRequestDto;
import com.novatech.cybertech.dto.response.wishlist.WishlistResponseDto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Tiny DTO factories for the wishlist surface.
 */
public final class WishlistDtoFixtures {

    private WishlistDtoFixtures() {
    }

    public static WishlistCreateRequestDto aValidCreateRequest() {
        return aValidCreateRequestBuilder().build();
    }

    public static WishlistCreateRequestDto.WishlistCreateRequestDtoBuilder aValidCreateRequestBuilder() {
        return WishlistCreateRequestDto.builder()
                .userUuid(UUID.randomUUID())
                .productUuid(UUID.randomUUID());
    }

    public static WishlistUpdateRequestDto aValidUpdateRequest() {
        WishlistUpdateRequestDto dto = new WishlistUpdateRequestDto();
        dto.setUuid(UUID.randomUUID());
        return dto;
    }

    public static WishlistResponseDto aSampleWishlistResponse() {
        return aSampleWishlistResponseBuilder().build();
    }

    public static WishlistResponseDto.WishlistResponseDtoBuilder aSampleWishlistResponseBuilder() {
        return WishlistResponseDto.builder()
                .uuid(UUID.randomUUID())
                .product(ProductDtoFixtures.aSampleProductResponse())
                .addedAt(LocalDateTime.now());
    }
}
