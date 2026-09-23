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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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

        try {
            return wishlistMapper.toResponseDto(wishlistRepository.save(wishlistEntity));
        } catch (DataIntegrityViolationException e) {
            // The pre-check above is a plain SELECT with no lock — a concurrent request (double
            // click, two tabs) can pass it for the same (user, product) before either insert
            // commits. The uk_wishlist_user_product DB constraint is what actually prevents the
            // duplicate in that case; surface it as the same domain exception the pre-check
            // throws instead of leaking a raw DataIntegrityViolationException as a 500.
            log.info("Concurrent add-to-wishlist race for user {} / product {} — caught by the DB constraint", userKeycloakId, productUuid);
            throw new ProductAlreadyInWishlist("Product already in wishlist");
        }
    }

    @Override
    @Transactional
    public void removeProductFromMyWishlist(String userKeycloakId, UUID productUuid) {
        WishlistEntity entity = wishlistRepository.findByUser_KeycloakIdAndProduct_Uuid(userKeycloakId, productUuid).orElseThrow(() -> new WishlistNotFoundException("Wishlist item not found"));
        wishlistRepository.delete(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<WishlistResponseDto> getMyWishlist(final String userKeycloakId, final Pageable pageable) {
        return wishlistRepository.findAllByUser_KeycloakId(userKeycloakId, pageable).map(wishlistMapper::toResponseDto);
    }
}