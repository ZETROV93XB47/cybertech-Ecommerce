package com.novatech.cybertech.services.implementation;

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

import java.util.ArrayList;
import java.util.List;

import static com.novatech.cybertech.constants.CyberTechAppConstants.DEFAULT_PAGE_SIZE_PRODUCT_SEARCH;
import static com.novatech.cybertech.utils.ProductSearchQueryUtils.attributeFilters;
import static com.novatech.cybertech.utils.ProductSearchQueryUtils.brandsFilter;
import static com.novatech.cybertech.utils.ProductSearchQueryUtils.categoryFilter;
import static com.novatech.cybertech.utils.ProductSearchQueryUtils.keywordQuery;
import static com.novatech.cybertech.utils.ProductSearchQueryUtils.numericRangeFilters;
import static com.novatech.cybertech.utils.ProductSearchQueryUtils.priceFilter;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductSearchServiceImp implements ProductSearchService {

    private static final int DEFAULT_PAGE = 0;

    private final ElasticsearchOperations elasticsearchOperations;

    @Override
    public Page<ProductDocument> search(final ProductSearchRequestDto req) {

        final int page = req.getPage() != null ? req.getPage() : DEFAULT_PAGE;
        final int size = req.getSize() != null ? req.getSize() : DEFAULT_PAGE_SIZE_PRODUCT_SEARCH;
        final Pageable pageable = PageRequest.of(page, size);

        // Criteria are built by ProductSearchQueryUtils, shared with the catalog facets.
        final List<Query> filters = new ArrayList<>();
        categoryFilter(req).ifPresent(filters::add);
        brandsFilter(req).ifPresent(filters::add);
        priceFilter(req).ifPresent(filters::add);
        filters.addAll(attributeFilters(req).values());
        filters.addAll(numericRangeFilters(req).values());
        final List<Query> musts = keywordQuery(req).stream().toList();

        final BoolQuery.Builder boolBuilder = new BoolQuery.Builder();
        if (!musts.isEmpty()) {
            boolBuilder.must(musts);
        }
        if (!filters.isEmpty()) {
            boolBuilder.filter(filters);
        }

        final NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(Query.of(q -> q.bool(boolBuilder.build())))
                .withPageable(pageable)
                .build();

        final SearchHits<ProductDocument> hits =
                elasticsearchOperations.search(nativeQuery, ProductDocument.class);

        final List<ProductDocument> content = hits.getSearchHits()
                .stream()
                .map(SearchHit::getContent)
                .toList();

        return new PageImpl<>(content, pageable, hits.getTotalHits());
    }
}
