package com.novatech.cybertech.services.implementation;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MultiMatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.novatech.cybertech.dto.request.search.ProductSearchRequestDto;
import com.novatech.cybertech.entities.document.ProductDocument;
import com.novatech.cybertech.services.core.ProductSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductSearchServiceImp implements ProductSearchService {

    private static final String DOT = ".";
    private static final String BRAND = "brand";
    private static final String PRICE = "price";
    private static final String NAME_FIELD = "name";
    private static final String CATEGORY = "category";
    private static final String ATTRIBUTES = "attributes";
    private static final String DESCRIPTION_FIELD = "description";

    private final ElasticsearchOperations elasticsearchOperations;

    @Override
    public List<ProductDocument> search(final ProductSearchRequestDto req) {

        BoolQuery.Builder bool = new BoolQuery.Builder();

        // 1) Catégorie (filter)
        if (req.getCategory() != null) {
            bool.filter(f -> f.term(t -> t.field(CATEGORY).value(req.getCategory().name())));
        }

        // 2) Marques (filter)
        if (req.getBrands() != null && !req.getBrands().isEmpty()) {
            List<String> brandValues = req.getBrands().stream().map(Enum::name).toList();

            bool.filter(f -> f.terms(t -> t
                    .field(BRAND)
                    .terms(v -> v.value(brandValues.stream().map(FieldValue::of).toList()))
            ));
        }

        // 3) Recherche textuelle (must)
        if (StringUtils.hasText(req.getKeyword())) {
            bool.must(m -> m.multiMatch(MultiMatchQuery.of(mm -> mm
                    .query(req.getKeyword())
                    .fields(NAME_FIELD, DESCRIPTION_FIELD)
                    .fuzziness("AUTO")
            )));
        }

        // 4) Range de prix (filter)
        if (req.getPriceMin() != null || req.getPriceMax() != null) {
            bool.filter(f -> f.range(r -> r.number(n -> {
                n.field(PRICE);
                if (req.getPriceMin() != null) n.gte(req.getPriceMin());
                if (req.getPriceMax() != null) n.lte(req.getPriceMax());
                return n;
            })));
        }

// 5) Attributs dynamiques (flattened) : match sur attributes.key
        if (req.getAttributes() != null) {
            req.getAttributes().forEach((key, values) -> {
                if (values != null && !values.isEmpty()) {
                    String field = ATTRIBUTES + DOT + key;

                    bool.filter(f -> f.bool(b -> {
                        values.forEach(val -> b.should(s -> s.match(m -> m
                                .field(field)
                                .query(String.valueOf(val)) // conversion safe
                        )));
                        return b;
                    }));
                }
            });
        }


        Query finalQuery = bool.build()._toQuery();

        NativeQuery query = NativeQuery.builder()
                .withQuery(finalQuery)
                .build();

        return elasticsearchOperations.search(query, ProductDocument.class)
                .map(SearchHit::getContent)
                .toList();
    }
}