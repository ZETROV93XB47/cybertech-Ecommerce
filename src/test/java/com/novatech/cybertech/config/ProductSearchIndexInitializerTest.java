package com.novatech.cybertech.config;

import com.novatech.cybertech.entities.document.ProductDocument;
import com.novatech.cybertech.repositories.ProductRepository;
import com.novatech.cybertech.repositories.ProductSearchRepository;
import com.novatech.cybertech.services.core.ProductSearchIndexService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductSearchIndexInitializerTest {

    private static final Map<String, Object> CURRENT_MAPPING = Map.of("properties", Map.of(
            "name", Map.of("type", "text", "fields", Map.of("keyword", Map.of("type", "keyword"))),
            "averageRating", Map.of("type", "double"),
            "uuid", Map.of("type", "keyword")));

    /** Index created before the sort: dynamic uuid (text + keyword), no name.keyword, no rating. */
    private static final Map<String, Object> LEGACY_MAPPING = Map.of("properties", Map.of(
            "name", Map.of("type", "text", "analyzer", "french"),
            "uuid", Map.of("type", "text", "fields", Map.of("keyword", Map.of("type", "keyword")))));

    @Mock private ElasticsearchOperations elasticsearchOperations;
    @Mock private ProductSearchRepository productSearchRepository;
    @Mock private ProductRepository productRepository;
    @Mock private ProductSearchIndexService productSearchIndexService;
    @Mock private IndexOperations indexOps;

    @InjectMocks
    private ProductSearchIndexInitializer initializer;

    @BeforeEach
    void indexOps() {
        // lenient: unused by the static hasSortableMapping test.
        lenient().when(elasticsearchOperations.indexOps(ProductDocument.class)).thenReturn(indexOps);
    }

    @Test
    @DisplayName("legacy mapping: index deleted, recreated with the current mapping, every product re-indexed")
    void legacyMappingIsRecreatedAndReindexed() {
        when(indexOps.exists()).thenReturn(true, false);
        when(indexOps.getMapping()).thenReturn(LEGACY_MAPPING);
        when(productSearchRepository.count()).thenReturn(0L);
        when(productRepository.count()).thenReturn(3L);

        initializer.run(null);

        final InOrder order = inOrder(indexOps, productSearchIndexService);
        order.verify(indexOps).delete();
        order.verify(indexOps).createWithMapping();
        order.verify(productSearchIndexService).reindexAll();
    }

    @Test
    @DisplayName("current mapping, documents present: nothing to do")
    void currentMappingIsKept() {
        when(indexOps.exists()).thenReturn(true);
        when(indexOps.getMapping()).thenReturn(CURRENT_MAPPING);
        when(productSearchRepository.count()).thenReturn(3L);

        initializer.run(null);

        verify(indexOps, never()).delete();
        verify(indexOps, never()).createWithMapping();
        verifyNoInteractions(productSearchIndexService);
    }

    @Test
    @DisplayName("current mapping but empty index while MySQL has products: re-indexed")
    void emptyIndexIsRefilled() {
        when(indexOps.exists()).thenReturn(true);
        when(indexOps.getMapping()).thenReturn(CURRENT_MAPPING);
        when(productSearchRepository.count()).thenReturn(0L);
        when(productRepository.count()).thenReturn(2L);

        initializer.run(null);

        verify(indexOps, never()).delete();
        verify(productSearchIndexService).reindexAll();
    }

    @Test
    @DisplayName("an Elasticsearch failure does not prevent the application from starting")
    void failureIsSwallowed() {
        when(indexOps.exists()).thenThrow(new IllegalStateException("ES down"));

        assertThatCode(() -> initializer.run(null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("hasSortableMapping requires name.keyword, averageRating and a keyword uuid")
    void sortableMappingDetection() {
        assertThat(ProductSearchIndexInitializer.hasSortableMapping(CURRENT_MAPPING)).isTrue();
        assertThat(ProductSearchIndexInitializer.hasSortableMapping(LEGACY_MAPPING)).isFalse();
        assertThat(ProductSearchIndexInitializer.hasSortableMapping(Map.of())).isFalse();
    }
}
