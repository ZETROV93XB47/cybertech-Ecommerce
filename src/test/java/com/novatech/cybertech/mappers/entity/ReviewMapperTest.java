package com.novatech.cybertech.mappers.entity;

import com.novatech.cybertech.dto.request.review.ReviewCreateRequestDto;
import com.novatech.cybertech.dto.request.review.ReviewUpdateRequestDto;
import com.novatech.cybertech.dto.response.review.ReviewResponseDto;
import com.novatech.cybertech.entities.ProductEntity;
import com.novatech.cybertech.entities.ReviewEntity;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.fixtures.builders.ProductEntityBuilder;
import com.novatech.cybertech.fixtures.builders.ReviewEntityBuilder;
import com.novatech.cybertech.fixtures.builders.UserEntityBuilder;
import com.novatech.cybertech.fixtures.dto.ReviewDtoFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ReviewMapper}. Covers BaseMapper-inherited overloads + the entity→response
 * surface that flattens nested user/product UUIDs.
 */
class ReviewMapperTest {

    private final ReviewMapper mapper = Mappers.getMapper(ReviewMapper.class);

    @Nested
    @DisplayName("mapFromCreationRequestToEntity(ReviewCreateRequestDto)")
    class FromCreationRequest {

        @Test
        void shouldMapRatingAndComment() {
            ReviewCreateRequestDto dto = ReviewDtoFixtures.aValidCreateRequestBuilder()
                    .rating(4).comment("ok").build();

            ReviewEntity entity = mapper.mapFromCreationRequestToEntity(dto);

            assertThat(entity).isNotNull();
            assertThat(entity.getRating()).isEqualTo(4);
            assertThat(entity.getComment()).isEqualTo("ok");
            // userUuid / productUuid / orderUuid have no field counterparts on ReviewEntity.
            assertThat(entity.getUserEntity()).isNull();
            assertThat(entity.getProductEntity()).isNull();
        }

        @Test
        void shouldReturnNullForNullSource() {
            assertThat(mapper.mapFromCreationRequestToEntity((ReviewCreateRequestDto) null)).isNull();
        }

        @Test
        void shouldMapCollection() {
            Collection<ReviewEntity> entities = mapper.mapFromCreationRequestToEntity(
                    List.of(ReviewDtoFixtures.aValidCreateRequest()));
            assertThat(entities).hasSize(1);
        }

        @Test
        void shouldReturnNullForNullCollection() {
            assertThat(mapper.mapFromCreationRequestToEntity((Collection<ReviewCreateRequestDto>) null)).isNull();
        }
    }

    @Nested
    @DisplayName("mapFromUpdateRequestToEntity(ReviewUpdateRequestDto)")
    class FromUpdateRequest {

        @Test
        void shouldMapMutableFields() {
            ReviewUpdateRequestDto dto = ReviewDtoFixtures.aValidUpdateRequestBuilder()
                    .rating(2).comment("meh").build();

            ReviewEntity entity = mapper.mapFromUpdateRequestToEntity(dto);

            assertThat(entity).isNotNull();
            assertThat(entity.getRating()).isEqualTo(2);
            assertThat(entity.getComment()).isEqualTo("meh");
        }

        @Test
        void shouldReturnNullForNullSource() {
            assertThat(mapper.mapFromUpdateRequestToEntity(null)).isNull();
        }
    }

    @Nested
    @DisplayName("mapFromEntityToResponseDto(ReviewEntity)")
    class ToResponseDto {

        @Test
        void shouldMapEntityToResponseDtoWithFlattenedRelations() {
            UUID userUuid = UUID.randomUUID();
            UUID productUuid = UUID.randomUUID();
            UserEntity user = UserEntityBuilder.aValidUserBuilder().uuid(userUuid).build();
            ProductEntity product = ProductEntityBuilder.aValidProductBuilder()
                    .uuid(productUuid).name("sample").build();
            ReviewEntity entity = ReviewEntityBuilder.aValidReviewBuilder()
                    .userEntity(user)
                    .productEntity(product)
                    .rating(5)
                    .comment("Great")
                    .build();

            ReviewResponseDto dto = mapper.mapFromEntityToResponseDto(entity);

            assertThat(dto).isNotNull();
            assertThat(dto.getUuid()).isEqualTo(entity.getUuid());
            assertThat(dto.getUserUuid()).isEqualTo(userUuid);
            assertThat(dto.getProductUuid()).isEqualTo(productUuid);
            assertThat(dto.getProductName()).isEqualTo("sample");
            assertThat(dto.getRating()).isEqualTo(5);
            assertThat(dto.getComment()).isEqualTo("Great");
        }

        @Test
        void shouldHandleNullUserAndProductGracefully() {
            ReviewEntity entity = ReviewEntityBuilder.aValidReviewBuilder()
                    .userEntity(null)
                    .productEntity(null)
                    .build();

            ReviewResponseDto dto = mapper.mapFromEntityToResponseDto(entity);

            assertThat(dto.getUserUuid()).isNull();
            assertThat(dto.getProductUuid()).isNull();
            assertThat(dto.getProductName()).isNull();
        }

        @Test
        void shouldReturnNullForNullSource() {
            assertThat(mapper.mapFromEntityToResponseDto((ReviewEntity) null)).isNull();
        }

        @Test
        void shouldMapCollection() {
            Collection<ReviewResponseDto> dtos = mapper.mapFromEntityToResponseDto(
                    List.of(ReviewEntityBuilder.aValidReview(), ReviewEntityBuilder.aValidReview()));

            assertThat(dtos).hasSize(2);
        }

        @Test
        void shouldReturnNullForNullCollection() {
            assertThat(mapper.mapFromEntityToResponseDto((Collection<ReviewEntity>) null)).isNull();
        }
    }
}
