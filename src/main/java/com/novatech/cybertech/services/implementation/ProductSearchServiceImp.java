package com.novatech.cybertech.services.implementation;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
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

        BoolQuery.Builder bool = new BoolQuery.Builder();
        Pageable pageable = PageRequest.of(req.getPage(), req.getSize());

        if (req.getCategory() != null) {
            bool.filter(f -> f.term(t -> t.field(CATEGORY).value(req.getCategory().name())));
        }

        if (req.getBrands() != null && !req.getBrands().isEmpty()) {
            List<FieldValue> brandValues = req.getBrands().stream()
                    .map(Enum::name)
                    .map(FieldValue::of)
                    .toList();

            bool.filter(f -> f.terms(t -> t
                    .field(BRAND)
                    .terms(v -> v.value(brandValues))
            ));
        }

        if (StringUtils.hasText(req.getKeyword())) {
            bool.must(m -> m.multiMatch(mm -> mm
                    .query(req.getKeyword())
                    .fields(NAME_FIELD, DESCRIPTION_FIELD)
                    .fuzziness("AUTO")
            ));
        }

        if (req.getPriceMin() != null || req.getPriceMax() != null) {
            bool.filter(f -> f.range(r -> r.number(n -> {
                n.field(PRICE);
                if (req.getPriceMin() != null) n.gte(req.getPriceMin());
                if (req.getPriceMax() != null) n.lte(req.getPriceMax());
                return n;
            })));
        }

        if (req.getAttributes() != null && req.getCategory() != null) {
            String categoryPrefix = "attributes." + req.getCategory().name() + ".";

            req.getAttributes().forEach((key, values) -> {
                if (values != null && !values.isEmpty()) {
                    String field = categoryPrefix + key + ".keyword";

                    bool.filter(f -> f.terms(t -> t
                            .field(field)
                            .terms(v -> v.value(
                                    values.stream()
                                            .map(String::valueOf)
                                            .map(FieldValue::of)
                                            .toList()
                            ))
                    ));
                }
            });
        }

        if (req.getNumericRanges() != null && req.getCategory() != null) {
            String categoryPrefix = "attributes." + req.getCategory().name() + ".";

            req.getNumericRanges().forEach((key, range) -> {
                String field = categoryPrefix + key;

                bool.filter(f -> f.range(r -> r.number(n -> {
                    n.field(field);
                    if (range.getMin() != null) n.gte(range.getMin());
                    if (range.getMax() != null) n.lte(range.getMax());
                    return n;
                })));
            });
        }

        NativeQuery query = NativeQuery.builder()
                .withQuery(bool.build()._toQuery())
                .withPageable(pageable)
                .build();

        SearchHits<ProductDocument> hits =
                elasticsearchOperations.search(query, ProductDocument.class);

        List<ProductDocument> content = hits.getSearchHits()
                .stream()
                .map(SearchHit::getContent)
                .toList();

        return new PageImpl<>(content, pageable, hits.getTotalHits());
    }
}