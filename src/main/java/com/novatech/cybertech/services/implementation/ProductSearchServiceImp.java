package com.novatech.cybertech.services.implementation;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.novatech.cybertech.dto.request.search.ProductSearchRequestDto;
import com.novatech.cybertech.entities.document.ProductDocument;
import com.novatech.cybertech.services.core.ProductSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductSearchServiceImp implements ProductSearchService {

    private static final String BRAND = "brand";
    private static final String PRICE = "price";
    private static final String NAME_FIELD = "name";
    private static final String CATEGORY = "category";
    private static final String DESCRIPTION_FIELD = "description";

    private final ElasticsearchOperations elasticsearchOperations;

    @Override
    public Page<ProductDocument> search(final ProductSearchRequestDto req) {

        List<Query> filters = new ArrayList<>();
        List<Query> musts = new ArrayList<>();
        Pageable pageable = PageRequest.of(req.getPage(), req.getSize());

        // Category filter
        if (req.getCategory() != null) {
            filters.add(new Query.Builder()
                    .term(t -> t.field(CATEGORY).value(req.getCategory().name()))
                    .build());
        }

        // Brands filter
        if (req.getBrands() != null && !req.getBrands().isEmpty()) {
            List<FieldValue> brandValues = req.getBrands().stream()
                    .map(Enum::name)
                    .map(FieldValue::of)
                    .toList();

            filters.add(new Query.Builder()
                    .terms(t -> t
                            .field(BRAND)
                            .terms(v -> v.value(brandValues))
                    )
                    .build());
        }

        // Keyword search (multiMatch)
        if (StringUtils.hasText(req.getKeyword())) {
            musts.add(new Query.Builder()
                    .multiMatch(mm -> mm
                            .query(req.getKeyword())
                            .fields(NAME_FIELD, DESCRIPTION_FIELD)
                            .fuzziness("AUTO")
                    )
                    .build());
        }

        // Price range filter
        if (req.getPriceMin() != null || req.getPriceMax() != null) {
            filters.add(new Query.Builder()
                    .range(r -> r.number(n -> {
                        n.field(PRICE);
                        if (req.getPriceMin() != null) n.gte(req.getPriceMin());
                        if (req.getPriceMax() != null) n.lte(req.getPriceMax());
                        return n;
                    }))
                    .build());
        }

        // Category-specific attributes filter
        if (req.getAttributes() != null && req.getCategory() != null) {
            String categoryPrefix = "attributes." + req.getCategory().name() + ".";

            req.getAttributes().forEach((key, values) -> {
                if (values != null && !values.isEmpty()) {
                    String field = categoryPrefix + key + ".keyword";

                    filters.add(new Query.Builder()
                            .terms(t -> t
                                    .field(field)
                                    .terms(v -> v.value(
                                            values.stream()
                                                    .map(String::valueOf)
                                                    .map(FieldValue::of)
                                                    .toList()
                                    ))
                            )
                            .build());
                }
            });
        }

        // Numeric ranges filter
        if (req.getNumericRanges() != null && req.getCategory() != null) {
            String categoryPrefix = "attributes." + req.getCategory().name() + ".";

            req.getNumericRanges().forEach((key, range) -> {
                String field = categoryPrefix + key;

                filters.add(new Query.Builder()
                        .range(r -> r.number(n -> {
                            n.field(field);
                            if (range.getMin() != null) n.gte(range.getMin());
                            if (range.getMax() != null) n.lte(range.getMax());
                            return n;
                        }))
                        .build());
            });
        }

        // Build BoolQuery
        BoolQuery.Builder boolBuilder = new BoolQuery.Builder();
        if (!musts.isEmpty()) {
            boolBuilder.must(musts);
        }
        if (!filters.isEmpty()) {
            boolBuilder.filter(filters);
        }

        BoolQuery boolQuery = boolBuilder.build();
        Query query = new Query.Builder().bool(boolQuery).build();

        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(query)
                .withPageable(pageable)
                .build();

        SearchHits<ProductDocument> hits =
                elasticsearchOperations.search(nativeQuery, ProductDocument.class);

        List<ProductDocument> content = hits.getSearchHits()
                .stream()
                .map(SearchHit::getContent)
                .toList();

        return new PageImpl<>(content, pageable, hits.getTotalHits());
    }
}