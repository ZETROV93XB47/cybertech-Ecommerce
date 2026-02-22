package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.dto.response.wishlist.WishlistResponseDto;
import com.novatech.cybertech.entities.ProductEntity;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.entities.WishlistEntity;
import com.novatech.cybertech.exceptions.ProductAlreadyInWishlist;
import com.novatech.cybertech.exceptions.ProductNotFoundException;
import com.novatech.cybertech.exceptions.UserNotFoundException;
import com.novatech.cybertech.exceptions.WishlistNotFoundException;
import com.novatech.cybertech.mappers.entity.WishlistMapper;
import com.novatech.cybertech.repositories.ProductRepository;
import com.novatech.cybertech.repositories.UserRepository;
import com.novatech.cybertech.repositories.WishlistRepository;
import com.novatech.cybertech.services.core.WishlistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WishlistServiceImp implements WishlistService {

    private final WishlistMapper wishlistMapper;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final WishlistRepository wishlistRepository;

    @Override
    @Transactional
    public WishlistResponseDto addProductToMyWishlist(String userKeycloakId, UUID productUuid) {
        if (wishlistRepository.existsByUser_KeycloakIdAndProduct_Uuid(userKeycloakId, productUuid)) {
            throw new ProductAlreadyInWishlist("Product already in wishlist");
        }

        UserEntity user = userRepository.findByKeycloakId(userKeycloakId).orElseThrow(() -> new UserNotFoundException("User not found"));
        ProductEntity product = productRepository.findByUuid(productUuid).orElseThrow(() -> new ProductNotFoundException("Product not found"));

        WishlistEntity wishlistEntity = WishlistEntity.builder()
                .user(user)
                .product(product)
                .addedAt(LocalDateTime.now())
                .build();

        return wishlistMapper.toResponseDto(wishlistRepository.save(wishlistEntity));
    }

    @Override
    @Transactional
    public void removeProductFromMyWishlist(String userKeycloakId, UUID productUuid) {
        WishlistEntity entity = wishlistRepository.findByUser_KeycloakIdAndProduct_Uuid(userKeycloakId, productUuid).orElseThrow(() -> new WishlistNotFoundException("Wishlist item not found"));
        wishlistRepository.delete(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Collection<WishlistResponseDto> getMyWishlist(String userKeycloakId) {
        return wishlistMapper.toResponseDtoList(wishlistRepository.findAllByUser_KeycloakId(userKeycloakId));
    }
}