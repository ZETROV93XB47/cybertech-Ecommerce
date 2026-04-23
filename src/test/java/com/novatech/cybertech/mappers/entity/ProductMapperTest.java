package com.novatech.cybertech.mappers.entity;

import com.novatech.cybertech.dto.request.product.ProductCreateRequestDto;
import com.novatech.cybertech.dto.request.product.ProductUpdateRequestDto;
import com.novatech.cybertech.dto.response.product.ProductResponseDto;
import com.novatech.cybertech.entities.ProductEntity;
import com.novatech.cybertech.entities.ReviewEntity;
import com.novatech.cybertech.entities.document.ProductDocument;
import com.novatech.cybertech.entities.enums.Brand;
import com.novatech.cybertech.entities.enums.Category;
import com.novatech.cybertech.fixtures.builders.ProductEntityBuilder;
import com.novatech.cybertech.fixtures.builders.ReviewEntityBuilder;
import com.novatech.cybertech.fixtures.dto.ProductDtoFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ProductMapper}.
 *
 * <p>Verifies BUG-017 fix (entity {@code photo} → response {@code photoUrl}) is in place.
 */
class ProductMapperTest {

    private final ProductMapper mapper = Mappers.getMapper(ProductMapper.class);

    @Nested
    @DisplayName("mapFromCreationRequestToEntity(ProductCreateRequestDto)")
    class FromCreationRequest {

        @Test
        void shouldMapAllFieldsAndForceReservedStockToZero() {
            Map<String, Object> attrs = new HashMap<>();
            attrs.put("ram", 16);
            ProductCreateRequestDto dto = ProductDtoFixtures.aValidCreateRequestBuilder()
                    .name("Laptop X")
                    .price(new BigDecimal("1299.99"))
                    .brand(Brand.HP)
                    .category(Category.COMPUTER)
                    .photo("https://cdn/x.jpg")
                    .stock(20)
                    .description("desc")
                    .attributes(attrs)
                    .build();

            ProductEntity entity = mapper.mapFromCreationRequestToEntity(dto);

            assertThat(entity).isNotNull();
            assertThat(entity.getName()).isEqualTo("Laptop X");
            assertThat(entity.getPrice()).isEqualByComparingTo("1299.99");
            assertThat(entity.getBrand()).isEqualTo(Brand.HP);
            assertThat(entity.getCategory()).isEqualTo(Category.COMPUTER);
            assertThat(entity.getPhoto()).isEqualTo("https://cdn/x.jpg");
            assertThat(entity.getStock()).isEqualTo(20);
            assertThat(entity.getReservedStock()).isZero();
            assertThat(entity.getDescription()).isEqualTo("desc");
            assertThat(entity.getAttributes()).containsEntry("ram", 16);
        }

        @Test
        void shouldReturnNullForNullSource() {
            assertThat(mapper.mapFromCreationRequestToEntity((ProductCreateRequestDto) null)).isNull();
        }

        @Test
        void shouldMapCollectionPreservingOrder() {
            ProductCreateRequestDto dto1 = ProductDtoFixtures.aValidCreateRequestBuilder().name("a").build();
            ProductCreateRequestDto dto2 = ProductDtoFixtures.aValidCreateRequestBuilder().name("b").build();

            Collection<ProductEntity> entities = mapper.mapFromCreationRequestToEntity(List.of(dto1, dto2));

            assertThat(entities).extracting(ProductEntity::getName).containsExactly("a", "b");
        }

        @Test
        void shouldReturnNullForNullCollection() {
            assertThat(mapper.mapFromCreationRequestToEntity((Collection<ProductCreateRequestDto>) null)).isNull();
        }
    }

    @Nested
    @DisplayName("mapFromUpdateRequestToEntity(ProductUpdateRequestDto)")
    class FromUpdateRequest {

        @Test
        void shouldMapMutableFields() {
            ProductUpdateRequestDto dto = ProductDtoFixtures.aValidUpdateRequest();

            ProductEntity entity = mapper.mapFromUpdateRequestToEntity(dto);

            assertThat(entity).isNotNull();
            assertThat(entity.getName()).isEqualTo(dto.getName());
            assertThat(entity.getPrice()).isEqualByComparingTo(dto.getPrice());
            assertThat(entity.getBrand()).isEqualTo(dto.getBrand());
            assertThat(entity.getCategory()).isEqualTo(dto.getCategory());
            assertThat(entity.getPhoto()).isEqualTo(dto.getPhoto());
            assertThat(entity.getStock()).isEqualTo(dto.getStock());
            assertThat(entity.getDescription()).isEqualTo(dto.getDescription());
        }

        @Test
        void shouldReturnNullForNullSource() {
            assertThat(mapper.mapFromUpdateRequestToEntity(null)).isNull();
        }
    }

    @Nested
    @DisplayName("mapFromEntityToResponseDto(ProductEntity)")
    class ToResponseDto {

        @Test
        void shouldMapEntityToResponseDtoWithAllFields() {
            UUID uuid = UUID.randomUUID();
            ProductEntity entity = ProductEntityBuilder.aValidProductBuilder()
                    .uuid(uuid)
                    .name("Sample")
                    .price(new BigDecimal("42.00"))
                    .brand(Brand.ASUS)
                    .category(Category.COMPUTER)
                    .photo("https://cdn/p.jpg")
                    .description("d")
                    .build();

            ProductResponseDto dto = mapper.mapFromEntityToResponseDto(entity);

            assertThat(dto).isNotNull();
            assertThat(dto.getUuid()).isEqualTo(uuid.toString());
            assertThat(dto.getName()).isEqualTo("Sample");
            assertThat(dto.getPrice()).isEqualByComparingTo("42.00");
            assertThat(dto.getBrand()).isEqualTo(Brand.ASUS.name());
            assertThat(dto.getCategory()).isEqualTo(Category.COMPUTER.name());
            assertThat(dto.getPhotoUrl()).isEqualTo("https://cdn/p.jpg");
            assertThat(dto.getDescription()).isEqualTo("d");
        }

        @Test
        void shouldMapPhotoToPhotoUrlFromEntity() {
            // BUG-017 fix verification (F2 wave). Prior to fix, photoUrl was null.
            ProductEntity entity = ProductEntityBuilder.aValidProductBuilder()
                    .photo("https://example.com/img.png")
                    .build();

            ProductResponseDto dto = mapper.mapFromEntityToResponseDto(entity);

            assertThat(dto.getPhotoUrl()).isEqualTo("https://example.com/img.png");
        }

        @Test
        void shouldHandleNullEnumsAndNullUuidGracefully() {
            ProductEntity entity = ProductEntityBuilder.aValidProductBuilder()
                    .uuid(null)
                    .brand(null)
                    .category(null)
                    .build();

            ProductResponseDto dto = mapper.mapFromEntityToResponseDto(entity);

            assertThat(dto.getUuid()).isNull();
            assertThat(dto.getBrand()).isNull();
            assertThat(dto.getCategory()).isNull();
        }

        @Test
        void shouldReturnNullForNullSource() {
            assertThat(mapper.mapFromEntityToResponseDto((ProductEntity) null)).isNull();
        }

        @Test
        void shouldMapCollectionOfEntities() {
            ProductEntity p1 = ProductEntityBuilder.aValidProductBuilder().name("A").build();
            ProductEntity p2 = ProductEntityBuilder.aValidProductBuilder().name("B").build();

            Collection<ProductResponseDto> dtos = mapper.mapFromEntityToResponseDto(List.of(p1, p2));

            assertThat(dtos).extracting(ProductResponseDto::getName).containsExactly("A", "B");
        }

        @Test
        void shouldReturnNullForNullCollection() {
            assertThat(mapper.mapFromEntityToResponseDto((Collection<ProductEntity>) null)).isNull();
        }
    }

    @Nested
    @DisplayName("mapFromProductDocumentToProductResponseDto(ProductDocument)")
    class FromDocument {

        @Test
        void shouldMapDocumentToResponseDto() {
            UUID uuid = UUID.randomUUID();
            ProductDocument doc = ProductDocument.builder()
                    .uuid(uuid)
                    .name("Doc product")
                    .description("doc desc")
                    .brand(Brand.DELL.name())
                    .category(Category.COMPUTER.name())
                    .price(new BigDecimal("199.00"))
                    .photoUrl("https://cdn/doc.jpg")
                    .build();

            ProductResponseDto dto = mapper.mapFromProductDocumentToProductResponseDto(doc);

            assertThat(dto).isNotNull();
            assertThat(dto.getUuid()).isEqualTo(uuid.toString());
            assertThat(dto.getName()).isEqualTo("Doc product");
            assertThat(dto.getBrand()).isEqualTo(Brand.DELL.name());
            assertThat(dto.getCategory()).isEqualTo(Category.COMPUTER.name());
            assertThat(dto.getPhotoUrl()).isEqualTo("https://cdn/doc.jpg");
            assertThat(dto.getPrice()).isEqualByComparingTo("199.00");
        }

        @Test
        void shouldReturnNullForNullSource() {
            assertThat(mapper.mapFromProductDocumentToProductResponseDto(null)).isNull();
        }
    }

    @Nested
    @DisplayName("mapFromProductEntityToProductDocument(ProductEntity)")
    class ToDocument {

        @Test
        void shouldMapEntityFieldsAndPhotoUrl() {
            UUID uuid = UUID.randomUUID();
            ProductEntity entity = ProductEntityBuilder.aValidProductBuilder()
                    .uuid(uuid)
                    .name("Sample")
                    .price(new BigDecimal("9.99"))
                    .photo("https://cdn/img.jpg")
                    .build();

            ProductDocument doc = mapper.mapFromProductEntityToProductDocument(entity);

            assertThat(doc).isNotNull();
            assertThat(doc.getId()).isEqualTo(uuid.toString());
            assertThat(doc.getUuid()).isEqualTo(uuid);
            assertThat(doc.getName()).isEqualTo("Sample");
            assertThat(doc.getBrand()).isEqualTo(entity.getBrand().name());
            assertThat(doc.getCategory()).isEqualTo(entity.getCategory().name());
            assertThat(doc.getPrice()).isEqualByComparingTo("9.99");
            assertThat(doc.getPhotoUrl()).isEqualTo("https://cdn/img.jpg");
            // attributes is annotated @Mapping(target = "attributes", ignore = true).
            assertThat(doc.getAttributes()).isNull();
        }

        @Test
        void shouldHandleNullUuid() {
            ProductEntity entity = ProductEntityBuilder.aValidProductBuilder().uuid(null).build();

            ProductDocument doc = mapper.mapFromProductEntityToProductDocument(entity);

            assertThat(doc).isNotNull();
            assertThat(doc.getId()).isNull();
            assertThat(doc.getUuid()).isNull();
        }

        @Test
        void shouldReturnNullForNullSource() {
            assertThat(mapper.mapFromProductEntityToProductDocument(null)).isNull();
        }
    }

    @Nested
    @DisplayName("default helpers — calculateAverageRating / calculateReviewCount")
    class Helpers {

        @Test
        void calculateAverageRating_shouldReturnZeroForNullList() {
            assertThat(mapper.calculateAverageRating(null)).isEqualTo(0.0);
        }

        @Test
        void calculateAverageRating_shouldReturnZeroForEmptyList() {
            assertThat(mapper.calculateAverageRating(List.of())).isEqualTo(0.0);
        }

        @Test
        void calculateAverageRating_shouldComputeMean() {
            ReviewEntity r1 = ReviewEntityBuilder.aValidReviewBuilder().rating(5).build();
            ReviewEntity r2 = ReviewEntityBuilder.aValidReviewBuilder().rating(3).build();

            assertThat(mapper.calculateAverageRating(List.of(r1, r2))).isEqualTo(4.0);
        }

        @Test
        void calculateReviewCount_shouldReturnZeroForNull() {
            assertThat(mapper.calculateReviewCount(null)).isZero();
        }

        @Test
        void calculateReviewCount_shouldReturnSize() {
            assertThat(mapper.calculateReviewCount(List.of(
                    ReviewEntityBuilder.aValidReview(),
                    ReviewEntityBuilder.aValidReview())))
                    .isEqualTo(2);
        }
    }
}
