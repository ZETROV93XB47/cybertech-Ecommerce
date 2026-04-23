package com.novatech.cybertech.services.implementation.shopping;

import com.novatech.cybertech.dto.response.wishlist.WishlistResponseDto;
import com.novatech.cybertech.entities.ProductEntity;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.entities.WishlistEntity;
import com.novatech.cybertech.exceptions.ProductAlreadyInWishlist;
import com.novatech.cybertech.exceptions.ProductNotFoundException;
import com.novatech.cybertech.exceptions.UserNotFoundException;
import com.novatech.cybertech.exceptions.WishlistNotFoundException;
import com.novatech.cybertech.fixtures.builders.ProductEntityBuilder;
import com.novatech.cybertech.fixtures.builders.UserEntityBuilder;
import com.novatech.cybertech.fixtures.builders.WishlistEntityBuilder;
import com.novatech.cybertech.mappers.entity.WishlistMapper;
import com.novatech.cybertech.repositories.ProductRepository;
import com.novatech.cybertech.repositories.UserRepository;
import com.novatech.cybertech.repositories.WishlistRepository;
import com.novatech.cybertech.services.implementation.WishlistServiceImp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Mockito unit tests for {@link WishlistServiceImp}.
 *
 * <p>SA-W3.4 wave — exhaustive coverage of add/remove/list. Documents BUG-430 — non-idempotent
 * remove (throws if entry already absent) — pinned via passing test asserting current behavior.</p>
 */
@ExtendWith(MockitoExtension.class)
class WishlistServiceImpTest {

    @Mock WishlistMapper wishlistMapper;
    @Mock UserRepository userRepository;
    @Mock ProductRepository productRepository;
    @Mock WishlistRepository wishlistRepository;

    @InjectMocks WishlistServiceImp service;

    String keycloakId;

    @BeforeEach
    void setUp() {
        keycloakId = "kc-" + UUID.randomUUID();
    }

    // =================================================================
    @Nested
    @DisplayName("addProductToMyWishlist")
    class AddProduct {

        @Test
        @DisplayName("happy path saves new wishlist entry with user/product/addedAt and returns mapped DTO")
        void happy_savesAndReturnsDto() {
            final UUID productUuid = UUID.randomUUID();
            final UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).build();
            final ProductEntity product = ProductEntityBuilder.aValidProductBuilder().uuid(productUuid).build();
            final WishlistEntity savedEntity = WishlistEntityBuilder.aValidWishlistBuilder().user(user).product(product).build();
            final WishlistResponseDto responseDto = new WishlistResponseDto();

            when(wishlistRepository.existsByUser_KeycloakIdAndProduct_Uuid(keycloakId, productUuid)).thenReturn(false);
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));
            when(productRepository.findByUuid(productUuid)).thenReturn(Optional.of(product));
            when(wishlistRepository.save(any(WishlistEntity.class))).thenReturn(savedEntity);
            when(wishlistMapper.toResponseDto(savedEntity)).thenReturn(responseDto);

            final WishlistResponseDto result = service.addProductToMyWishlist(keycloakId, productUuid);

            assertThat(result).isSameAs(responseDto);

            final ArgumentCaptor<WishlistEntity> captor = ArgumentCaptor.forClass(WishlistEntity.class);
            verify(wishlistRepository).save(captor.capture());
            final WishlistEntity built = captor.getValue();
            assertThat(built.getUser()).isSameAs(user);
            assertThat(built.getProduct()).isSameAs(product);
            assertThat(built.getAddedAt()).isNotNull();
        }

        @Test
        @DisplayName("duplicate (existsByUser+Product=true) throws ProductAlreadyInWishlist before any save")
        void duplicate_throws() {
            final UUID productUuid = UUID.randomUUID();
            when(wishlistRepository.existsByUser_KeycloakIdAndProduct_Uuid(keycloakId, productUuid)).thenReturn(true);

            assertThatThrownBy(() -> service.addProductToMyWishlist(keycloakId, productUuid))
                    .isInstanceOf(ProductAlreadyInWishlist.class)
                    .hasMessageContaining("already");

            verify(wishlistRepository, never()).save(any());
            verifyNoInteractions(userRepository, productRepository, wishlistMapper);
        }

        @Test
        @DisplayName("user not found throws UserNotFoundException")
        void userMissing_throws() {
            final UUID productUuid = UUID.randomUUID();
            when(wishlistRepository.existsByUser_KeycloakIdAndProduct_Uuid(keycloakId, productUuid)).thenReturn(false);
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.addProductToMyWishlist(keycloakId, productUuid))
                    .isInstanceOf(UserNotFoundException.class);

            verify(wishlistRepository, never()).save(any());
        }

        @Test
        @DisplayName("product not found throws ProductNotFoundException")
        void productMissing_throws() {
            final UUID productUuid = UUID.randomUUID();
            final UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).build();
            when(wishlistRepository.existsByUser_KeycloakIdAndProduct_Uuid(keycloakId, productUuid)).thenReturn(false);
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));
            when(productRepository.findByUuid(productUuid)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.addProductToMyWishlist(keycloakId, productUuid))
                    .isInstanceOf(ProductNotFoundException.class);

            verify(wishlistRepository, never()).save(any());
        }
    }

    // =================================================================
    @Nested
    @DisplayName("removeProductFromMyWishlist")
    class RemoveProduct {

        @Test
        @DisplayName("happy path deletes the resolved wishlist entity")
        void happy_deletes() {
            final UUID productUuid = UUID.randomUUID();
            final WishlistEntity entity = WishlistEntityBuilder.aValidWishlist();
            when(wishlistRepository.findByUser_KeycloakIdAndProduct_Uuid(keycloakId, productUuid))
                    .thenReturn(Optional.of(entity));

            service.removeProductFromMyWishlist(keycloakId, productUuid);

            verify(wishlistRepository).delete(entity);
        }

        @Test
        @DisplayName("BUG-430 (PIN): removing a non-existent entry throws WishlistNotFoundException — not idempotent")
        void notFound_throwsInsteadOfNoOp_BUG430() {
            final UUID productUuid = UUID.randomUUID();
            when(wishlistRepository.findByUser_KeycloakIdAndProduct_Uuid(keycloakId, productUuid))
                    .thenReturn(Optional.empty());

            // PIN current (non-idempotent) behavior: a typical UI "click heart again to un-favorite"
            // would naturally double-fire. A fully idempotent remove (no-op when absent) would be
            // friendlier — but the implementation throws. Pinning until fixed.
            assertThatThrownBy(() -> service.removeProductFromMyWishlist(keycloakId, productUuid))
                    .isInstanceOf(WishlistNotFoundException.class)
                    .hasMessageContaining("not found");

            verify(wishlistRepository, never()).delete(any());
        }
    }

    // =================================================================
    @Nested
    @DisplayName("getMyWishlist")
    class GetMyWishlist {

        @Test
        @DisplayName("happy path delegates to repository and maps page elements")
        void happy_delegates() {
            final Pageable pageable = PageRequest.of(0, 10);
            final WishlistEntity e1 = WishlistEntityBuilder.aValidWishlist();
            final WishlistEntity e2 = WishlistEntityBuilder.aValidWishlist();
            final Page<WishlistEntity> page = new PageImpl<>(List.of(e1, e2), pageable, 2);
            final WishlistResponseDto dto1 = new WishlistResponseDto();
            final WishlistResponseDto dto2 = new WishlistResponseDto();

            when(wishlistRepository.findAllByUser_KeycloakId(keycloakId, pageable)).thenReturn(page);
            when(wishlistMapper.toResponseDto(e1)).thenReturn(dto1);
            when(wishlistMapper.toResponseDto(e2)).thenReturn(dto2);

            final Page<WishlistResponseDto> result = service.getMyWishlist(keycloakId, pageable);

            assertThat(result.getContent()).containsExactly(dto1, dto2);
            assertThat(result.getTotalElements()).isEqualTo(2);
        }

        @Test
        @DisplayName("empty result returns empty page, no mapper invocation")
        void empty_returnsEmptyPage() {
            final Pageable pageable = PageRequest.of(0, 10);
            when(wishlistRepository.findAllByUser_KeycloakId(keycloakId, pageable)).thenReturn(Page.empty());

            final Page<WishlistResponseDto> result = service.getMyWishlist(keycloakId, pageable);

            assertThat(result.getContent()).isEmpty();
            verifyNoInteractions(wishlistMapper);
        }

        @Test
        @DisplayName("propagates repository exceptions")
        void repoThrows_propagates() {
            final Pageable pageable = PageRequest.of(0, 5);
            when(wishlistRepository.findAllByUser_KeycloakId(keycloakId, pageable))
                    .thenThrow(new RuntimeException("db-down"));

            assertThatThrownBy(() -> service.getMyWishlist(keycloakId, pageable))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("db-down");
        }
    }
}
