package com.novatech.cybertech.services.implementation;

import com.networknt.schema.JsonSchema;
import com.novatech.cybertech.dto.request.product.ProductCategorySchemaCreateRequestDto;
import com.novatech.cybertech.dto.request.product.ProductCategorySchemaUpdateRequestDto;
import com.novatech.cybertech.dto.response.product.ProductCategorySchemaResponseDto;
import com.novatech.cybertech.entities.ProductCategorySchemaEntity;
import com.novatech.cybertech.exceptions.UnknownProductCategoryException;
import com.novatech.cybertech.repositories.ProductCategorySchemaRepository;
import com.novatech.cybertech.services.core.ProductCategorySchemaCache;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Optional;

import static com.novatech.cybertech.constants.CyberTechAppConstants.PRODUCT_CATEGORY_SCHEMA_CHANGED_CHANNEL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductCategorySchemaServiceImpTest {

    private static final String SCHEMA_TEXT = """
            {"type": "object", "properties": {"cpu": {"type": "string"}}}
            """;

    @Mock private ProductCategorySchemaRepository productCategorySchemaRepository;
    @Mock private ProductCategorySchemaCache productCategorySchemaCache;
    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private JsonSchema compiledSchema;

    @InjectMocks
    private ProductCategorySchemaServiceImp service;

    private ProductCategorySchemaEntity entity(final String categoryKey) {
        return ProductCategorySchemaEntity.builder()
                .categoryKey(categoryKey)
                .label(categoryKey)
                .jsonSchema(SCHEMA_TEXT)
                .active(true)
                .build();
    }

    @Nested
    @DisplayName("getAll / getByCategoryKey")
    class Reads {

        @Test
        @DisplayName("getAll returns every schema sorted by categoryKey")
        void getAllSorted() {
            when(productCategorySchemaRepository.findAll())
                    .thenReturn(List.of(entity("MONITOR"), entity("COMPUTER")));

            final List<ProductCategorySchemaResponseDto> result = service.getAll();

            assertThat(result).extracting(ProductCategorySchemaResponseDto::categoryKey)
                    .containsExactly("COMPUTER", "MONITOR");
        }

        @Test
        @DisplayName("getByCategoryKey throws UnknownProductCategoryException when absent")
        void getByCategoryKeyNotFound() {
            when(productCategorySchemaRepository.findByCategoryKey("TABLET")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getByCategoryKey("TABLET"))
                    .isInstanceOf(UnknownProductCategoryException.class);
        }
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("compiles before saving, invalidates cache, publishes the changed categoryKey")
        void happyPath() {
            final ProductCategorySchemaCreateRequestDto request =
                    new ProductCategorySchemaCreateRequestDto("TABLET", "Tablets", SCHEMA_TEXT);
            when(productCategorySchemaRepository.existsByCategoryKey("TABLET")).thenReturn(false);
            when(productCategorySchemaCache.compile(SCHEMA_TEXT)).thenReturn(compiledSchema);
            when(productCategorySchemaRepository.save(any(ProductCategorySchemaEntity.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            final ProductCategorySchemaResponseDto result = service.create(request);

            assertThat(result.categoryKey()).isEqualTo("TABLET");
            verify(productCategorySchemaCache).compile(SCHEMA_TEXT);
            verify(productCategorySchemaCache).invalidate("TABLET");
            verify(stringRedisTemplate).convertAndSend(PRODUCT_CATEGORY_SCHEMA_CHANGED_CHANNEL, "TABLET");
        }

        @Test
        @DisplayName("duplicate categoryKey throws IllegalArgumentException and never saves")
        void duplicateThrows() {
            final ProductCategorySchemaCreateRequestDto request =
                    new ProductCategorySchemaCreateRequestDto("COMPUTER", "Computers", SCHEMA_TEXT);
            when(productCategorySchemaRepository.existsByCategoryKey("COMPUTER")).thenReturn(true);

            assertThatThrownBy(() -> service.create(request))
                    .isInstanceOf(IllegalArgumentException.class);

            verify(productCategorySchemaRepository, never()).save(any());
            verify(productCategorySchemaCache, never()).compile(anyString());
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("re-compiles only when jsonSchema is supplied, then invalidates + publishes")
        void updatesJsonSchema() {
            final ProductCategorySchemaEntity existing = entity("COMPUTER");
            when(productCategorySchemaRepository.findByCategoryKey("COMPUTER")).thenReturn(Optional.of(existing));
            when(productCategorySchemaCache.compile(anyString())).thenReturn(compiledSchema);
            when(productCategorySchemaRepository.save(any(ProductCategorySchemaEntity.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            final String newSchema = "{\"type\": \"object\"}";
            service.update("COMPUTER", new ProductCategorySchemaUpdateRequestDto(null, newSchema, null));

            final ArgumentCaptor<ProductCategorySchemaEntity> captor = ArgumentCaptor.forClass(ProductCategorySchemaEntity.class);
            verify(productCategorySchemaRepository).save(captor.capture());
            assertThat(captor.getValue().getJsonSchema()).isEqualTo(newSchema);
            verify(productCategorySchemaCache).compile(newSchema);
            verify(productCategorySchemaCache).invalidate("COMPUTER");
            verify(stringRedisTemplate).convertAndSend(eq(PRODUCT_CATEGORY_SCHEMA_CHANGED_CHANNEL), eq("COMPUTER"));
        }

        @Test
        @DisplayName("null fields are left unchanged and the schema is not re-compiled")
        void nullFieldsUnchanged() {
            final ProductCategorySchemaEntity existing = entity("COMPUTER");
            when(productCategorySchemaRepository.findByCategoryKey("COMPUTER")).thenReturn(Optional.of(existing));
            when(productCategorySchemaRepository.save(any(ProductCategorySchemaEntity.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            service.update("COMPUTER", new ProductCategorySchemaUpdateRequestDto(null, null, false));

            verify(productCategorySchemaCache, never()).compile(anyString());
            final ArgumentCaptor<ProductCategorySchemaEntity> captor = ArgumentCaptor.forClass(ProductCategorySchemaEntity.class);
            verify(productCategorySchemaRepository).save(captor.capture());
            assertThat(captor.getValue().getJsonSchema()).isEqualTo(SCHEMA_TEXT);
            assertThat(captor.getValue().isActive()).isFalse();
        }

        @Test
        @DisplayName("unknown categoryKey throws and never saves")
        void unknownThrows() {
            when(productCategorySchemaRepository.findByCategoryKey("TABLET")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.update("TABLET", new ProductCategorySchemaUpdateRequestDto("x", null, null)))
                    .isInstanceOf(UnknownProductCategoryException.class);

            verify(productCategorySchemaRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("deleteByCategoryKey")
    class Delete {

        @Test
        @DisplayName("deletes the row, invalidates cache, publishes the changed categoryKey")
        void happyPath() {
            final ProductCategorySchemaEntity existing = entity("COMPUTER");
            when(productCategorySchemaRepository.findByCategoryKey("COMPUTER")).thenReturn(Optional.of(existing));

            service.deleteByCategoryKey("COMPUTER");

            verify(productCategorySchemaRepository).delete(existing);
            verify(productCategorySchemaCache).invalidate("COMPUTER");
            verify(stringRedisTemplate).convertAndSend(PRODUCT_CATEGORY_SCHEMA_CHANGED_CHANNEL, "COMPUTER");
        }

        @Test
        @DisplayName("unknown categoryKey throws and never deletes")
        void unknownThrows() {
            when(productCategorySchemaRepository.findByCategoryKey("TABLET")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.deleteByCategoryKey("TABLET"))
                    .isInstanceOf(UnknownProductCategoryException.class);

            verify(productCategorySchemaRepository, never()).delete(any());
        }
    }
}
