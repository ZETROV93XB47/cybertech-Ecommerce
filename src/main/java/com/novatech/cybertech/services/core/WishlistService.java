package com.novatech.cybertech.services.core;

import com.novatech.cybertech.dto.response.wishlist.WishlistResponseDto;

import java.util.Collection;
import java.util.UUID;

public interface WishlistService {

    WishlistResponseDto addProductToMyWishlist(String userKeycloakId, UUID productUuid);

    void removeProductFromMyWishlist(String userKeycloakId, UUID productUuid);

    Collection<WishlistResponseDto> getMyWishlist(String userKeycloakId);
}