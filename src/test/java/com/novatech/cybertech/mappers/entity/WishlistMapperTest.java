package com.novatech.cybertech.mappers.entity;

import com.novatech.cybertech.dto.response.product.ProductResponseDto;
import com.novatech.cybertech.dto.response.wishlist.WishlistResponseDto;
import com.novatech.cybertech.entities.ProductEntity;
import com.novatech.cybertech.entities.WishlistEntity;
import com.novatech.cybertech.fixtures.builders.ProductEntityBuilder;
import com.novatech.cybertech.fixtures.builders.UserEntityBuilder;
import com.novatech.cybertech.fixtures.builders.WishlistEntityBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link WishlistMapper}. The mapper uses {@code uses = ProductMapper.class},
 * which {@code Mappers.getMapper(...)} expresses as an {@code @Autowired} field on the generated
 * impl. We reflectively inject a {@code ProductMapperImpl} since there is no Spring context here
 * (pattern carried from SA1.5).
 */
class WishlistMapperTest {

    private WishlistMapper mapper;

    @BeforeEach
    void setUp() throws Exception {
        mapper = Mappers.getMapper(WishlistMapper.class);
        ProductMapper productMapper = Mappers.getMapper(ProductMapper.class);
        Field field = mapper.getClass().getDeclaredField("productMapper");
        field.setAccessible(true);
        field.set(mapper, productMapper);
    }

    @Nested
    @DisplayName("toResponseDto(WishlistEntity)")
    class ToResponseDto {

        @Test
        void shouldMapEntityToResponseDtoWithProduct() {
            UUID wishlistUuid = UUID.randomUUID();
            UUID productUuid = UUID.randomUUID();
            ProductEntity product = ProductEntityBuilder.aValidProductBuilder()
                    .uuid(productUuid)
                    .name("Wishlisted product")
                    .build();
            WishlistEntity entity = WishlistEntityBuilder.aValidWishlistBuilder()
                    .uuid(wishlistUuid)
                    .user(UserEntityBuilder.aValidUser())
                    .product(product)
                    .addedAt(LocalDateTime.now())
                    .build();

            WishlistResponseDto dto = mapper.toResponseDto(entity);

            assertThat(dto).isNotNull();
            assertThat(dto.getUuid()).isEqualTo(wishlistUuid);
            assertThat(dto.getAddedAt()).isEqualTo(entity.getAddedAt());
            assertThat(dto.getProduct()).isNotNull();
            assertThat(dto.getProduct().getUuid()).isEqualTo(productUuid.toString());
            assertThat(dto.getProduct().getName()).isEqualTo("Wishlisted product");
            assertThat(dto.getProduct().getPhotoUrl()).isEqualTo(product.getPhoto());
        }

        @Test
        void shouldHandleNullProductByDelegating() {
            // The delegated ProductMapper#mapFromEntityToResponseDto returns null for null input.
            WishlistEntity entity = WishlistEntityBuilder.aValidWishlistBuilder()
                    .product(null)
                    .build();

            WishlistResponseDto dto = mapper.toResponseDto(entity);

            assertThat(dto).isNotNull();
            assertThat(dto.getProduct()).isNull();
            assertThat(dto.getUuid()).isEqualTo(entity.getUuid());
        }

        @Test
        void shouldReturnNullForNullSource() {
            assertThat(mapper.toResponseDto(null)).isNull();
        }
    }

    @Nested
    @DisplayName("toResponseDtoList(Collection<WishlistEntity>)")
    class ToResponseDtoList {

        @Test
        void shouldMapEachElement() {
            WishlistEntity a = WishlistEntityBuilder.aValidWishlist();
            WishlistEntity b = WishlistEntityBuilder.aValidWishlist();

            List<WishlistResponseDto> dtos = mapper.toResponseDtoList(List.of(a, b));

            assertThat(dtos).hasSize(2);
            assertThat(dtos).extracting(WishlistResponseDto::getUuid)
                    .containsExactly(a.getUuid(), b.getUuid());
        }

        @Test
        void shouldReturnEmptyListForEmptyInput() {
            List<WishlistResponseDto> dtos = mapper.toResponseDtoList(List.<WishlistEntity>of());

            assertThat(dtos).isNotNull().isEmpty();
        }

        @Test
        void shouldReturnNullForNullSource() {
            assertThat(mapper.toResponseDtoList((Collection<WishlistEntity>) null)).isNull();
        }
    }
}
