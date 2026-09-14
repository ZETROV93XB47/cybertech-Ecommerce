package com.novatech.cybertech.services.implementation;

import com.networknt.schema.JsonSchema;
import com.novatech.cybertech.entities.ProductCategorySchemaEntity;
import com.novatech.cybertech.exceptions.InvalidProductCategorySchemaException;
import com.novatech.cybertech.exceptions.UnknownProductCategoryException;
import com.novatech.cybertech.repositories.ProductCategorySchemaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductCategorySchemaCacheImpTest {

    private static final String VALID_SCHEMA = """
            {"type": "object", "properties": {"cpu": {"type": "string"}}, "required": ["cpu"]}
            """;

    @Mock private ProductCategorySchemaRepository productCategorySchemaRepository;

    @InjectMocks
    private ProductCategorySchemaCacheImp cache;

    private ProductCategorySchemaEntity schemaRow(final String categoryKey, final boolean active) {
        return ProductCategorySchemaEntity.builder()
                .categoryKey(categoryKey)
                .label(categoryKey)
                .jsonSchema(VALID_SCHEMA)
                .active(active)
                .build();
    }

    @Nested
    @DisplayName("loadAll")
    class LoadAll {

        @Test
        @DisplayName("populates the cache from every active row, no lazy DB hit on subsequent get()")
        void populatesFromActiveRows() {
            when(productCategorySchemaRepository.findByActiveTrue())
                    .thenReturn(List.of(schemaRow("COMPUTER", true)));

            cache.loadAll();
            final JsonSchema schema = cache.get("COMPUTER");

            assertThat(schema).isNotNull();
            verify(productCategorySchemaRepository, never()).findByCategoryKey("COMPUTER");
        }
    }

    @Nested
    @DisplayName("get — lazy reload on cache miss")
    class Get {

        @Test
        @DisplayName("cache miss reloads from the repository and compiles")
        void missReloadsFromRepository() {
            when(productCategorySchemaRepository.findByCategoryKey("MONITOR"))
                    .thenReturn(Optional.of(schemaRow("MONITOR", true)));

            final JsonSchema schema = cache.get("MONITOR");

            assertThat(schema).isNotNull();
            verify(productCategorySchemaRepository).findByCategoryKey("MONITOR");
        }

        @Test
        @DisplayName("second call for the same key does not hit the repository again")
        void secondCallIsCached() {
            when(productCategorySchemaRepository.findByCategoryKey("MONITOR"))
                    .thenReturn(Optional.of(schemaRow("MONITOR", true)));

            cache.get("MONITOR");
            cache.get("MONITOR");

            verify(productCategorySchemaRepository).findByCategoryKey("MONITOR");
        }

        @Test
        @DisplayName("unknown category throws UnknownProductCategoryException")
        void unknownCategoryThrows() {
            when(productCategorySchemaRepository.findByCategoryKey("TABLET")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> cache.get("TABLET"))
                    .isInstanceOf(UnknownProductCategoryException.class);
        }

        @Test
        @DisplayName("inactive category is treated as absent")
        void inactiveCategoryThrows() {
            when(productCategorySchemaRepository.findByCategoryKey("KEYBOARD"))
                    .thenReturn(Optional.of(schemaRow("KEYBOARD", false)));

            assertThatThrownBy(() -> cache.get("KEYBOARD"))
                    .isInstanceOf(UnknownProductCategoryException.class);
        }
    }

    @Nested
    @DisplayName("invalidate")
    class Invalidate {

        @Test
        @DisplayName("forces the next get() to reload from the repository")
        void forcesReload() {
            when(productCategorySchemaRepository.findByCategoryKey("MONITOR"))
                    .thenReturn(Optional.of(schemaRow("MONITOR", true)));
            cache.get("MONITOR");

            cache.invalidate("MONITOR");
            cache.get("MONITOR");

            verify(productCategorySchemaRepository, org.mockito.Mockito.times(2)).findByCategoryKey("MONITOR");
        }
    }

    @Nested
    @DisplayName("compile")
    class Compile {

        @Test
        @DisplayName("valid JSON Schema text compiles successfully")
        void validSchemaCompiles() {
            assertThat(cache.compile(VALID_SCHEMA)).isNotNull();
        }

        @Test
        @DisplayName("malformed JSON throws InvalidProductCategorySchemaException")
        void malformedJsonThrows() {
            assertThatThrownBy(() -> cache.compile("{not valid json"))
                    .isInstanceOf(InvalidProductCategorySchemaException.class);
        }
    }
}
