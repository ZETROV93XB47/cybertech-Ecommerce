package com.novatech.cybertech.services.core;

import com.novatech.cybertech.dto.response.wishlist.WishlistResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface WishlistService {

    WishlistResponseDto addProductToMyWishlist(String userKeycloakId, UUID productUuid);

    void removeProductFromMyWishlist(String userKeycloakId, UUID productUuid);

    Page<WishlistResponseDto> getMyWishlist(String userKeycloakId, Pageable pageable);
}