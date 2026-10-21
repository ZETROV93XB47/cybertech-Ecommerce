package com.novatech.cybertech.config;

import com.novatech.cybertech.entities.document.ProductDocument;
import com.novatech.cybertech.repositories.ProductRepository;
import com.novatech.cybertech.repositories.ProductSearchRepository;
import com.novatech.cybertech.services.core.ProductSearchIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.novatech.cybertech.utils.ProductSearchQueryUtils.AVERAGE_RATING_FIELD;
import static com.novatech.cybertech.utils.ProductSearchQueryUtils.NAME_FIELD;
import static com.novatech.cybertech.utils.ProductSearchQueryUtils.UUID_FIELD;

/**
 * Brings the {@code products} index up to the {@link ProductDocument} mapping at startup, then fills
 * it from MySQL when needed:
 * <ul>
 *   <li>index created before the catalog sort (no {@code name.keyword}, no {@code averageRating},
 *       {@code uuid} not a keyword) → deleted, recreated with the current settings and mapping, and
 *       every product re-indexed with its ratings. A text field cannot become sortable in place.</li>
 *   <li>index up to date but empty while MySQL holds products (e.g. Elasticsearch volume reset) →
 *       every product re-indexed.</li>
 * </ul>
 * Best effort: an Elasticsearch failure is logged and does not prevent the application from starting.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductSearchIndexInitializer implements ApplicationRunner {

    private static final String PROPERTIES = "properties";
    private static final String FIELDS = "fields";
    private static final String TYPE = "type";
    private static final String KEYWORD_TYPE = "keyword";
    private static final String KEYWORD_SUB_FIELD = "keyword";

    private final ElasticsearchOperations elasticsearchOperations;
    private final ProductSearchRepository productSearchRepository;
    private final ProductRepository productRepository;
    private final ProductSearchIndexService productSearchIndexService;

    @Override
    public void run(final ApplicationArguments args) {
        try {
            migrate();
        } catch (RuntimeException e) {
            log.warn("Product search index could not be checked or migrated at startup", e);
        }
    }

    private void migrate() {
        final IndexOperations indexOps = elasticsearchOperations.indexOps(ProductDocument.class);
        if (indexOps.exists() && !hasSortableMapping(indexOps.getMapping())) {
            log.info("Product search index predates the catalog sort — recreating it and re-indexing every product");
            indexOps.delete();
        }
        if (!indexOps.exists()) {
            indexOps.createWithMapping();
        }
        if (productSearchRepository.count() == 0 && productRepository.count() > 0) {
            productSearchIndexService.reindexAll();
        }
    }

    /** {@code name.keyword}, {@code averageRating} and a keyword {@code uuid} are all mapped. */
    static boolean hasSortableMapping(final Map<String, Object> mapping) {
        final Map<?, ?> properties = asMap(mapping.get(PROPERTIES));
        final Map<?, ?> nameFields = asMap(asMap(properties.get(NAME_FIELD)).get(FIELDS));
        return nameFields.containsKey(KEYWORD_SUB_FIELD)
                && properties.containsKey(AVERAGE_RATING_FIELD)
                && KEYWORD_TYPE.equals(asMap(properties.get(UUID_FIELD)).get(TYPE));
    }

    private static Map<?, ?> asMap(final Object value) {
        return value instanceof Map<?, ?> map ? map : Map.of();
    }
}
