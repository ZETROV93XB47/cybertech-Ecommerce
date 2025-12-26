package com.novatech.cybertech.services.implementation;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import com.novatech.cybertech.entities.ProductDocument;
import com.novatech.cybertech.entities.enums.Category;
import com.novatech.cybertech.services.core.ProductSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductSearchServiceImp implements ProductSearchService {

    private static final String DOT_SEPARATOR = ".";
    private static final String CATEGORY = "category";
    private static final String ATTRIBUTES = "attributes";

    private final ElasticsearchOperations elasticsearchOperations;


    @Override
    public List<ProductDocument> searchByAttributes(final Category category, final Map<String, String> filters) {

        // 1. On commence par filtrer par catégorie
        final BoolQuery.Builder boolQuery = new BoolQuery.Builder().filter(f -> f.term(t -> t.field(CATEGORY).value(category.toString())));

        log.info("filters : {}", filters);

//        // 2. On ajoute dynamiquement les filtres de la Map "attributes"
//        filters.forEach((key, value) -> boolQuery.must(m -> m.term(t -> t
//                .field(ATTRIBUTES + DOT_SEPARATOR + key) // On accède directement au champ imbriqué
//                .value(value)
//        )));

        filters.forEach((key, value) -> boolQuery.must(m -> m.term(t -> t
                .field(ATTRIBUTES + "." + key + ".keyword")
                .value(value)
        )));



        final NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.bool(boolQuery.build()))
                .build();

        return elasticsearchOperations.search(query, ProductDocument.class)
                .map(SearchHit::getContent)
                .toList();
    }
}