package com.novatech.cybertech.services.implementation.catalog;

import com.novatech.cybertech.dto.request.product.ProductCreateRequestDto;
import com.novatech.cybertech.dto.request.product.ProductUpdateRequestDto;
import com.novatech.cybertech.dto.response.product.ProductResponseDto;
import com.novatech.cybertech.entities.ProductEntity;
import com.novatech.cybertech.entities.document.ProductDocument;
import com.novatech.cybertech.entities.enums.Brand;
import com.novatech.cybertech.entities.enums.Category;
import com.novatech.cybertech.entities.validator.ProductValidationService;
import com.novatech.cybertech.exceptions.ProductNotFoundException;
import com.novatech.cybertech.fixtures.builders.ProductEntityBuilder;
import com.novatech.cybertech.fixtures.dto.ProductDtoFixtures;
import com.novatech.cybertech.mappers.entity.ProductMapper;
import com.novatech.cybertech.repositories.ProductRepository;
import com.novatech.cybertech.repositories.ProductSearchRepository;
import com.novatech.cybertech.services.core.AttributesFactory;
import com.novatech.cybertech.services.core.ProductSearchService;
import com.novatech.cybertech.services.core.S3Service;
import com.novatech.cybertech.services.implementation.ProductManagementServiceImp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Mockito unit tests for {@link ProductManagementServiceImp}.
 *
 * <p>SA-W3.5 wave — services/catalog. Source-verified BUG-080 / BUG-180-product / BUG-081 fix
 * status (F2 wave 2026-04-23T10:30Z): {@code update()} now uses {@code lockByUuid} +
 * IGNORE-style merge + ES re-index; {@code deleteByUUIDs()} now iterates UUIDs and removes
 * each {@code ProductDocument} from ES. F1 handoff that "update does NOT re-index into ES"
 * is REFUTED by direct read of the file (lines 110-111).
 */
@ExtendWith(MockitoExtension.class)
class ProductManagementServiceImpTest {

    @Mock S3Service s3Service;
    @Mock ProductMapper productMapper;
    @Mock AttributesFactory attributesFactory;
    @Mock ProductRepository productRepository;
    @Mock ProductSearchService productSearchService;
    @Mock ProductSearchRepository productSearchRepository;
    @Mock ProductValidationService productValidationService;

    @InjectMocks ProductManagementServiceImp service;

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("happy path saves SQL entity then ES document, returns mapped response")
        void create_happyPath_savesSqlAndEs() {
            ProductCreateRequestDto req = ProductDtoFixtures.aValidCreateRequest();
            ProductEntity mapped = ProductEntityBuilder.aValidProduct();
            ProductEntity saved = ProductEntityBuilder.aValidProduct();
            ProductDocument doc = ProductDocument.builder().uuid(UUID.randomUUID()).build();
            ProductResponseDto expected = ProductDtoFixtures.aSampleProductResponse();

            when(productMapper.mapFromCreationRequestToEntity(req)).thenReturn(mapped);
            when(productRepository.save(mapped)).thenReturn(saved);
            when(productMapper.mapFromProductEntityToProductDocument(saved)).thenReturn(doc);
            when(attributesFactory.create(eq(req.getCategory()), eq(req.getAttributes()))).thenReturn(new HashMap<>());
            when(productMapper.mapFromEntityToResponseDto(saved)).thenReturn(expected);

            ProductResponseDto result = service.create(req);

            assertThat(result).isSameAs(expected);
            InOrder inOrder = inOrder(productValidationService, productRepository, productSearchRepository);
            inOrder.verify(productValidationService).validateAttributes(req.getCategory(), req.getAttributes());
            inOrder.verify(productRepository).save(mapped);
            inOrder.verify(productSearchRepository).save(doc);
            assertThat(doc.getAttributes()).isNotNull();
        }

        @Test
        @DisplayName("validation failure short-circuits before save")
        void create_validationFails_noSave() {
            ProductCreateRequestDto req = ProductDtoFixtures.aValidCreateRequest();
            org.mockito.Mockito.doThrow(new IllegalArgumentException("bad"))
                    .when(productValidationService).validateAttributes(any(), any());

            assertThatThrownBy(() -> service.create(req)).isInstanceOf(IllegalArgumentException.class);

            verifyNoInteractions(productRepository);
            verifyNoInteractions(productSearchRepository);
        }

        @Test
        @DisplayName("ES failure propagates after SQL save")
        void create_esFailure_propagates() {
            ProductCreateRequestDto req = ProductDtoFixtures.aValidCreateRequest();
            ProductEntity mapped = ProductEntityBuilder.aValidProduct();
            ProductEntity saved = ProductEntityBuilder.aValidProduct();
            ProductDocument doc = ProductDocument.builder().build();

            when(productMapper.mapFromCreationRequestToEntity(req)).thenReturn(mapped);
            when(productRepository.save(mapped)).thenReturn(saved);
            when(productMapper.mapFromProductEntityToProductDocument(saved)).thenReturn(doc);
            when(attributesFactory.create(any(), any())).thenReturn(new HashMap<>());
            when(productSearchRepository.save(doc)).thenThrow(new RuntimeException("ES down"));

            assertThatThrownBy(() -> service.create(req))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("ES down");
            verify(productRepository).save(mapped);
        }
    }

    @Nested
    @DisplayName("createWithImage")
    class CreateWithImage {

        @Test
        @DisplayName("happy path uploads image then sets photo url before delegating to create")
        void createWithImage_happyPath_uploadsThenCreates() {
            ProductCreateRequestDto req = ProductDtoFixtures.aValidCreateRequest();
            MockMultipartFile file = new MockMultipartFile("photo", "p.jpg", "image/jpeg", new byte[]{1, 2, 3});

            when(s3Service.uploadFile(file, "products")).thenReturn("https://s3/p.jpg");
            ProductEntity mapped = ProductEntityBuilder.aValidProduct();
            ProductEntity saved = ProductEntityBuilder.aValidProduct();
            when(productMapper.mapFromCreationRequestToEntity(any(ProductCreateRequestDto.class))).thenReturn(mapped);
            when(productRepository.save(mapped)).thenReturn(saved);
            when(productMapper.mapFromProductEntityToProductDocument(saved)).thenReturn(ProductDocument.builder().build());
            when(attributesFactory.create(any(), any())).thenReturn(new HashMap<>());
            when(productMapper.mapFromEntityToResponseDto(saved)).thenReturn(ProductDtoFixtures.aSampleProductResponse());

            service.createWithImage(req, file);

            assertThat(req.getPhoto()).isEqualTo("https://s3/p.jpg");
            verify(s3Service).uploadFile(file, "products");
        }

        @Test
        @DisplayName("null image skips upload and delegates straight to create")
        void createWithImage_nullImage_skipsUpload() {
            ProductCreateRequestDto req = ProductDtoFixtures.aValidCreateRequest();
            String original = req.getPhoto();
            ProductEntity mapped = ProductEntityBuilder.aValidProduct();
            ProductEntity saved = ProductEntityBuilder.aValidProduct();
            when(productMapper.mapFromCreationRequestToEntity(any(ProductCreateRequestDto.class))).thenReturn(mapped);
            when(productRepository.save(mapped)).thenReturn(saved);
            when(productMapper.mapFromProductEntityToProductDocument(saved)).thenReturn(ProductDocument.builder().build());
            when(attributesFactory.create(any(), any())).thenReturn(new HashMap<>());
            when(productMapper.mapFromEntityToResponseDto(saved)).thenReturn(ProductDtoFixtures.aSampleProductResponse());

            service.createWithImage(req, null);

            assertThat(req.getPhoto()).isEqualTo(original);
            verifyNoInteractions(s3Service);
        }

        @Test
        @DisplayName("empty image skips upload")
        void createWithImage_emptyImage_skipsUpload() {
            ProductCreateRequestDto req = ProductDtoFixtures.aValidCreateRequest();
            MockMultipartFile empty = new MockMultipartFile("photo", "p.jpg", "image/jpeg", new byte[0]);
            ProductEntity mapped = ProductEntityBuilder.aValidProduct();
            ProductEntity saved = ProductEntityBuilder.aValidProduct();
            when(productMapper.mapFromCreationRequestToEntity(any(ProductCreateRequestDto.class))).thenReturn(mapped);
            when(productRepository.save(mapped)).thenReturn(saved);
            when(productMapper.mapFromProductEntityToProductDocument(saved)).thenReturn(ProductDocument.builder().build());
            when(attributesFactory.create(any(), any())).thenReturn(new HashMap<>());
            when(productMapper.mapFromEntityToResponseDto(saved)).thenReturn(ProductDtoFixtures.aSampleProductResponse());

            service.createWithImage(req, empty);

            verifyNoInteractions(s3Service);
        }
    }

    @Nested
    @DisplayName("update — BUG-080 / BUG-180-product fix verification")
    class Update {

        @Test
        @DisplayName("uses lockByUuid (BUG-080 fix) and re-indexes into ES (BUG-180-product fix)")
        void update_happyPath_locksAndReindexes() {
            ProductUpdateRequestDto req = ProductDtoFixtures.aValidUpdateRequest();
            ProductEntity existing = ProductEntityBuilder.aValidProductBuilder().uuid(req.getProductUuid()).build();
            ProductEntity saved = ProductEntityBuilder.aValidProductBuilder().uuid(req.getProductUuid()).build();
            ProductDocument doc = ProductDocument.builder().uuid(req.getProductUuid()).build();
            ProductResponseDto expected = ProductDtoFixtures.aSampleProductResponse();

            when(productRepository.lockByUuid(req.getProductUuid())).thenReturn(Optional.of(existing));
            when(productRepository.save(existing)).thenReturn(saved);
            when(productMapper.mapFromProductEntityToProductDocument(saved)).thenReturn(doc);
            when(productMapper.mapFromEntityToResponseDto(saved)).thenReturn(expected);

            ProductResponseDto result = service.update(req);

            assertThat(result).isSameAs(expected);
            InOrder inOrder = inOrder(productRepository, productSearchRepository);
            inOrder.verify(productRepository).lockByUuid(req.getProductUuid());
            inOrder.verify(productRepository).save(existing);
            inOrder.verify(productSearchRepository).save(doc);
            verify(productRepository, never()).findByUuid(any());
        }

        @Test
        @DisplayName("missing product throws ProductNotFoundException — never saves")
        void update_notFound_throws() {
            ProductUpdateRequestDto req = ProductDtoFixtures.aValidUpdateRequest();
            when(productRepository.lockByUuid(req.getProductUuid())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.update(req))
                    .isInstanceOf(ProductNotFoundException.class)
                    .hasMessageContaining(req.getProductUuid().toString());

            verify(productRepository, never()).save(any());
            verifyNoInteractions(productSearchRepository);
        }

        @Test
        @DisplayName("IGNORE merge — only non-null DTO fields overwrite existing entity")
        void update_ignoreMerge_onlyNonNullFieldsApplied() {
            UUID id = UUID.randomUUID();
            ProductUpdateRequestDto req = new ProductUpdateRequestDto();
            req.setProductUuid(id);
            req.setName("New Name");
            ProductEntity existing = ProductEntityBuilder.aValidProductBuilder()
                    .uuid(id)
                    .name("Old Name")
                    .price(new BigDecimal("11.11"))
                    .brand(Brand.HP)
                    .category(Category.MONITOR)
                    .photo("old-photo")
                    .stock(7)
                    .description("old desc")
                    .build();

            when(productRepository.lockByUuid(id)).thenReturn(Optional.of(existing));
            when(productRepository.save(existing)).thenReturn(existing);
            when(productMapper.mapFromProductEntityToProductDocument(existing)).thenReturn(ProductDocument.builder().build());
            when(productMapper.mapFromEntityToResponseDto(existing)).thenReturn(ProductDtoFixtures.aSampleProductResponse());

            service.update(req);

            assertThat(existing.getName()).isEqualTo("New Name");
            assertThat(existing.getPrice()).isEqualByComparingTo(new BigDecimal("11.11"));
            assertThat(existing.getBrand()).isEqualTo(Brand.HP);
            assertThat(existing.getCategory()).isEqualTo(Category.MONITOR);
            assertThat(existing.getPhoto()).isEqualTo("old-photo");
            assertThat(existing.getStock()).isEqualTo(7);
            assertThat(existing.getDescription()).isEqualTo("old desc");
        }

        @Test
        @DisplayName("all DTO fields non-null overwrites every field on existing entity")
        void update_allFieldsApplied() {
            ProductUpdateRequestDto req = ProductDtoFixtures.aValidUpdateRequest();
            ProductEntity existing = ProductEntityBuilder.aValidProductBuilder().uuid(req.getProductUuid()).build();

            when(productRepository.lockByUuid(req.getProductUuid())).thenReturn(Optional.of(existing));
            when(productRepository.save(existing)).thenReturn(existing);
            when(productMapper.mapFromProductEntityToProductDocument(existing)).thenReturn(ProductDocument.builder().build());
            when(productMapper.mapFromEntityToResponseDto(existing)).thenReturn(ProductDtoFixtures.aSampleProductResponse());

            service.update(req);

            assertThat(existing.getName()).isEqualTo(req.getName());
            assertThat(existing.getPrice()).isEqualTo(req.getPrice());
            assertThat(existing.getBrand()).isEqualTo(req.getBrand());
            assertThat(existing.getCategory()).isEqualTo(req.getCategory());
            assertThat(existing.getPhoto()).isEqualTo(req.getPhoto());
            assertThat(existing.getStock()).isEqualTo(req.getStock());
            assertThat(existing.getDescription()).isEqualTo(req.getDescription());
        }
    }

    @Nested
    @DisplayName("deleteByUUID")
    class DeleteByUUID {

        @Test
        @DisplayName("happy path deletes from SQL and ES")
        void deleteByUUID_happyPath_bothSinks() {
            UUID id = UUID.randomUUID();
            service.deleteByUUID(id);

            InOrder inOrder = inOrder(productRepository, productSearchRepository);
            inOrder.verify(productRepository).deleteByUuid(id);
            inOrder.verify(productSearchRepository).deleteByUuid(id);
        }

        @Test
        @DisplayName("ES failure after SQL delete propagates")
        void deleteByUUID_esFailure_propagates() {
            UUID id = UUID.randomUUID();
            org.mockito.Mockito.doThrow(new RuntimeException("ES boom"))
                    .when(productSearchRepository).deleteByUuid(id);

            assertThatThrownBy(() -> service.deleteByUUID(id)).isInstanceOf(RuntimeException.class);
            verify(productRepository).deleteByUuid(id);
        }
    }

    @Nested
    @DisplayName("deleteByUUIDs — BUG-081 fix verification (per-UUID ES cleanup)")
    class DeleteByUUIDs {

        @Test
        @DisplayName("bulk delete also removes each ProductDocument from ES (BUG-081 fixed)")
        void deleteByUUIDs_cleansEsPerUuid() {
            UUID a = UUID.randomUUID();
            UUID b = UUID.randomUUID();
            UUID c = UUID.randomUUID();
            List<UUID> ids = List.of(a, b, c);

            service.deleteByUUIDs(ids);

            verify(productRepository).deleteAllByUuidIn(ids);
            verify(productSearchRepository).deleteByUuid(a);
            verify(productSearchRepository).deleteByUuid(b);
            verify(productSearchRepository).deleteByUuid(c);
            verify(productSearchRepository, times(3)).deleteByUuid(any(UUID.class));
        }

        @Test
        @DisplayName("null collection only touches SQL (defensive guard)")
        void deleteByUUIDs_nullCollection_onlySql() {
            service.deleteByUUIDs(null);

            verify(productRepository).deleteAllByUuidIn(null);
            verifyNoInteractions(productSearchRepository);
        }

        @Test
        @DisplayName("empty collection is a no-op against ES")
        void deleteByUUIDs_emptyCollection_noEsCalls() {
            service.deleteByUUIDs(List.of());

            verify(productRepository).deleteAllByUuidIn(List.of());
            verify(productSearchRepository, never()).deleteByUuid(any(UUID.class));
        }
    }

    @Nested
    @DisplayName("getByUUID")
    class GetByUUID {

        @Test
        @DisplayName("happy path returns mapped DTO")
        void getByUUID_happyPath() {
            UUID id = UUID.randomUUID();
            ProductEntity entity = ProductEntityBuilder.aValidProductBuilder().uuid(id).build();
            ProductResponseDto expected = ProductDtoFixtures.aSampleProductResponse();

            when(productRepository.findByUuid(id)).thenReturn(Optional.of(entity));
            when(productMapper.mapFromEntityToResponseDto(entity)).thenReturn(expected);

            assertThat(service.getByUUID(id)).isSameAs(expected);
        }

        @Test
        @DisplayName("missing throws ProductNotFoundException with the requested uuid")
        void getByUUID_notFound_throws() {
            UUID id = UUID.randomUUID();
            when(productRepository.findByUuid(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getByUUID(id))
                    .isInstanceOf(ProductNotFoundException.class)
                    .hasMessageContaining(id.toString());
        }
    }

    @Nested
    @DisplayName("getAll")
    class GetAll {

        @Test
        @DisplayName("returns mapped collection from repository.findAll()")
        void getAll_happyPath() {
            List<ProductEntity> entities = List.of(ProductEntityBuilder.aValidProduct(), ProductEntityBuilder.aValidProduct());
            List<ProductResponseDto> responses = List.of(ProductDtoFixtures.aSampleProductResponse(), ProductDtoFixtures.aSampleProductResponse());
            when(productRepository.findAll()).thenReturn(entities);
            when(productMapper.mapFromEntityToResponseDto(entities)).thenReturn(responses);

            assertThat(service.getAll()).isEqualTo(responses);
        }

        @Test
        @DisplayName("returns empty list when repository is empty")
        void getAll_empty() {
            when(productRepository.findAll()).thenReturn(List.of());
            when(productMapper.mapFromEntityToResponseDto(List.<ProductEntity>of())).thenReturn(List.of());

            assertThat(service.getAll()).isEmpty();
        }
    }

    @Nested
    @DisplayName("getByUUIDs")
    class GetByUUIDs {

        @Test
        @DisplayName("returns mapped collection")
        void getByUUIDs_happyPath() {
            UUID a = UUID.randomUUID();
            UUID b = UUID.randomUUID();
            List<UUID> ids = List.of(a, b);
            List<ProductEntity> entities = List.of(ProductEntityBuilder.aValidProductBuilder().uuid(a).build());
            List<ProductResponseDto> responses = List.of(ProductDtoFixtures.aSampleProductResponse());

            when(productRepository.findAllByUuidIn(ids)).thenReturn(entities);
            when(productMapper.mapFromEntityToResponseDto(entities)).thenReturn(responses);

            assertThat(service.getByUUIDs(ids)).isEqualTo(responses);
        }
    }

    @Nested
    @DisplayName("searchProducts — delegation to ProductSearchService")
    class SearchProducts {

        @Test
        @DisplayName("delegates to ProductSearchService and maps documents to responses")
        void searchProducts_delegates() {
            com.novatech.cybertech.dto.request.search.ProductSearchRequestDto req =
                    com.novatech.cybertech.dto.request.search.ProductSearchRequestDto.builder()
                            .category(Category.COMPUTER).page(0).size(10).build();
            ProductDocument doc = ProductDocument.builder().uuid(UUID.randomUUID()).name("X").build();
            ProductResponseDto resp = ProductDtoFixtures.aSampleProductResponse();
            org.springframework.data.domain.Page<ProductDocument> page =
                    new org.springframework.data.domain.PageImpl<>(List.of(doc));

            when(productSearchService.search(req)).thenReturn(page);
            when(productMapper.mapFromProductDocumentToProductResponseDto(doc)).thenReturn(resp);

            org.springframework.data.domain.Page<ProductResponseDto> result = service.searchProducts(req);

            assertThat(result.getContent()).containsExactly(resp);
        }
    }

    @Nested
    @DisplayName("getBestSellers")
    class GetBestSellers {

        @Test
        @DisplayName("returns mapped page from findBestSellers")
        void getBestSellers_happy() {
            org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 5);
            ProductEntity entity = ProductEntityBuilder.aValidProduct();
            ProductResponseDto resp = ProductDtoFixtures.aSampleProductResponse();
            org.springframework.data.domain.Page<ProductEntity> page =
                    new org.springframework.data.domain.PageImpl<>(List.of(entity), pageable, 1);

            when(productRepository.findBestSellers(pageable)).thenReturn(page);
            when(productMapper.mapFromEntityToResponseDto(entity)).thenReturn(resp);

            org.springframework.data.domain.Page<ProductResponseDto> result = service.getBestSellers(pageable);

            assertThat(result.getContent()).containsExactly(resp);
        }
    }
}
