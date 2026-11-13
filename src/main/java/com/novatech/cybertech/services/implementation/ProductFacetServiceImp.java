package com.novatech.cybertech.services.implementation;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.util.NamedValue;
import com.fasterxml.jackson.databind.JsonNode;
import com.novatech.cybertech.dto.request.search.ProductSearchRequestDto;
import com.novatech.cybertech.dto.response.search.FacetValueDto;
import com.novatech.cybertech.dto.response.search.ProductFacetsResponseDto;
import com.novatech.cybertech.dto.response.search.RangeFacetDto;
import com.novatech.cybertech.entities.document.ProductDocument;
import com.novatech.cybertech.services.core.ProductCategorySchemaCache;
import com.novatech.cybertech.services.core.ProductFacetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.StreamSupport;

import static com.novatech.cybertech.constants.CyberTechAppConstants.FACET_MAX_VALUES;
import static com.novatech.cybertech.utils.ProductSearchQueryUtils.BRAND_FIELD;
import static com.novatech.cybertech.utils.ProductSearchQueryUtils.PRICE_FIELD;
import static com.novatech.cybertech.utils.ProductSearchQueryUtils.attributeFilters;
import static com.novatech.cybertech.utils.ProductSearchQueryUtils.attributeKeywordField;
import static com.novatech.cybertech.utils.ProductSearchQueryUtils.attributeNumericField;
import static com.novatech.cybertech.utils.ProductSearchQueryUtils.brandsFilter;
import static com.novatech.cybertech.utils.ProductSearchQueryUtils.categoryFilter;
import static com.novatech.cybertech.utils.ProductSearchQueryUtils.keywordQuery;
import static com.novatech.cybertech.utils.ProductSearchQueryUtils.numericRangeFilters;
import static com.novatech.cybertech.utils.ProductSearchQueryUtils.priceFilter;

/**
 * See {@link ProductFacetService}.
 *
 * <p>One Elasticsearch request, no hits ({@code size 0}): the query holds only the criteria every
 * facet shares (keyword, category); each facet is a {@code filter} aggregation carrying every other
 * active filter, with a {@code terms} or {@code min}/{@code max} sub-aggregation. That is the
 * disjunctive counting of a catalog: ticking LENOVO does not hide the other brands.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductFacetServiceImp implements ProductFacetService {

    // Facet ids, used both to exclude a facet's own filter and as aggregation names. Attribute
    // aggregations are named by position, never by key: a schema key may hold characters an
    // aggregation name forbids ("[", "]", ">").
    private static final String ALL_FILTERS_AGG = "all";
    private static final String BRANDS_AGG = "brands";
    private static final String PRICE_AGG = "price";
    private static final String TEXT_ATTRIBUTE_AGG_PREFIX = "attr_";
    private static final String NUMERIC_ATTRIBUTE_AGG_PREFIX = "num_";
    // Prefix of the filter id of a request attribute the schema does not declare: it never names
    // an aggregation, so it restricts every facet.
    private static final String UNDECLARED_ATTRIBUTE_FILTER_PREFIX = "undeclared_";
    private static final String VALUES_SUB_AGG = "values";
    private static final String MIN_SUB_AGG = "min";
    private static final String MAX_SUB_AGG = "max";

    private static final String SCHEMA_PROPERTIES = "properties";
    private static final String SCHEMA_TYPE = "type";
    private static final String SCHEMA_TYPE_STRING = "string";
    private static final String SCHEMA_TYPE_INTEGER = "integer";
    private static final String SCHEMA_TYPE_NUMBER = "number";
    private static final String SCHEMA_TYPE_NULL = "null";

    private static final String COUNT_ORDER = "_count";
    private static final String KEY_ORDER = "_key";

    private static final Comparator<FacetValueDto> BY_COUNT_THEN_VALUE =
            Comparator.comparingLong(FacetValueDto::getCount).reversed().thenComparing(FacetValueDto::getValue);

    private final ElasticsearchOperations elasticsearchOperations;
    private final ProductCategorySchemaCache productCategorySchemaCache;

    @Override
    public ProductFacetsResponseDto getFacets(final ProductSearchRequestDto req) {
        // Resolved first: an unknown category fails before any Elasticsearch call.
        final SchemaAttributes schemaAttributes = req.getCategory() == null
                ? null
                : readSchemaAttributes(productCategorySchemaCache.get(req.getCategory()).getSchemaNode());
        final List<String> textKeys = schemaAttributes == null ? List.of() : schemaAttributes.textKeys();
        final List<String> numericKeys = schemaAttributes == null ? List.of() : schemaAttributes.numericKeys();

        // Facet filters, keyed by the id of the facet each one belongs to.
        final Map<String, Query> facetFilters = new LinkedHashMap<>();
        brandsFilter(req).ifPresent(f -> facetFilters.put(BRANDS_AGG, f));
        priceFilter(req).ifPresent(f -> facetFilters.put(PRICE_AGG, f));
        attributeFilters(req).forEach((key, f) -> facetFilters.put(attributeFilterId(TEXT_ATTRIBUTE_AGG_PREFIX, key, textKeys), f));
        numericRangeFilters(req).forEach((key, f) -> facetFilters.put(attributeFilterId(NUMERIC_ATTRIBUTE_AGG_PREFIX, key, numericKeys), f));

        final NativeQueryBuilder queryBuilder = NativeQuery.builder()
                .withQuery(Query.of(q -> q.bool(b -> b
                        .must(keywordQuery(req).stream().toList())
                        .filter(categoryFilter(req).stream().toList()))))
                .withMaxResults(0)
                .withAggregation(ALL_FILTERS_AGG, filterAggregation(facetFilters, null, Map.of()))
                .withAggregation(BRANDS_AGG, filterAggregation(facetFilters, BRANDS_AGG, Map.of(VALUES_SUB_AGG, termsAggregation(BRAND_FIELD))))
                .withAggregation(PRICE_AGG, filterAggregation(facetFilters, PRICE_AGG, minMaxAggregations(PRICE_FIELD)));
        for (int i = 0; i < textKeys.size(); i++) {
            final String id = TEXT_ATTRIBUTE_AGG_PREFIX + i;
            queryBuilder.withAggregation(id, filterAggregation(facetFilters, id,
                    Map.of(VALUES_SUB_AGG, termsAggregation(attributeKeywordField(textKeys.get(i))))));
        }
        for (int i = 0; i < numericKeys.size(); i++) {
            final String id = NUMERIC_ATTRIBUTE_AGG_PREFIX + i;
            queryBuilder.withAggregation(id, filterAggregation(facetFilters, id, minMaxAggregations(attributeNumericField(numericKeys.get(i)))));
        }

        final SearchHits<ProductDocument> hits = elasticsearchOperations.search(queryBuilder.build(), ProductDocument.class);
        final Map<String, Aggregate> results = hits.getAggregations() instanceof ElasticsearchAggregations esAggregations
                ? aggregates(esAggregations)
                : Map.of();

        final ProductFacetsResponseDto response = ProductFacetsResponseDto.builder()
                .totalHits(filterBucket(results, ALL_FILTERS_AGG).map(a -> a.filter().docCount()).orElse(0L))
                .brands(termsValues(results, BRANDS_AGG))
                .price(range(results, PRICE_AGG))
                .build();
        if (schemaAttributes != null) {
            final Map<String, List<FacetValueDto>> attributes = new LinkedHashMap<>();
            for (int i = 0; i < textKeys.size(); i++) {
                attributes.put(textKeys.get(i), termsValues(results, TEXT_ATTRIBUTE_AGG_PREFIX + i));
            }
            final Map<String, RangeFacetDto> numericAttributes = new LinkedHashMap<>();
            for (int i = 0; i < numericKeys.size(); i++) {
                numericAttributes.put(numericKeys.get(i), range(results, NUMERIC_ATTRIBUTE_AGG_PREFIX + i));
            }
            response.setAttributes(attributes);
            response.setNumericAttributes(numericAttributes);
        }

        log.info("Product facets {}: {} hit(s), {} brand(s)", req, response.getTotalHits(), response.getBrands().size());
        return response;
    }

    /**
     * Text and numeric attribute keys of a category JSON Schema, in declaration order. A nullable
     * type ({@code ["string", "null"]}) counts as its non-null type; any other type is not a facet.
     */
    private static SchemaAttributes readSchemaAttributes(final JsonNode schemaNode) {
        final List<String> textKeys = new ArrayList<>();
        final List<String> numericKeys = new ArrayList<>();
        final JsonNode properties = schemaNode == null ? null : schemaNode.get(SCHEMA_PROPERTIES);
        if (properties != null) {
            properties.fields().forEachRemaining(property -> {
                switch (schemaType(property.getValue())) {
                    case SCHEMA_TYPE_STRING -> textKeys.add(property.getKey());
                    case SCHEMA_TYPE_INTEGER, SCHEMA_TYPE_NUMBER -> numericKeys.add(property.getKey());
                    default -> { /* boolean, object, array: no facet */ }
                }
            });
        }
        return new SchemaAttributes(textKeys, numericKeys);
    }

    private static String schemaType(final JsonNode propertySchema) {
        final JsonNode type = propertySchema.get(SCHEMA_TYPE);
        if (type == null) {
            return "";
        }
        if (type.isArray()) {
            return StreamSupport.stream(type.spliterator(), false)
                    .map(JsonNode::asText)
                    .filter(t -> !SCHEMA_TYPE_NULL.equals(t))
                    .findFirst()
                    .orElse("");
        }
        return type.asText();
    }

    // A request filter on a key the schema declares shares that facet's id, so the facet ignores
    // it; a filter on any other key gets an id no facet has, so it still restricts every facet.
    private static String attributeFilterId(final String aggregationPrefix, final String key, final List<String> schemaKeys) {
        final int index = schemaKeys.indexOf(key);
        return index >= 0 ? aggregationPrefix + index : UNDECLARED_ATTRIBUTE_FILTER_PREFIX + aggregationPrefix + key;
    }

    /** A filter aggregation holding every facet filter except the one of {@code excludedFacetId}. */
    private static Aggregation filterAggregation(final Map<String, Query> facetFilters, final String excludedFacetId,
                                                 final Map<String, Aggregation> subAggregations) {
        final List<Query> others = facetFilters.entrySet().stream()
                .filter(e -> !e.getKey().equals(excludedFacetId))
                .map(Map.Entry::getValue)
                .toList();
        final Query filter = others.isEmpty()
                ? Query.of(q -> q.matchAll(m -> m))
                : Query.of(q -> q.bool(b -> b.filter(others)));
        return Aggregation.of(a -> a.filter(filter).aggregations(subAggregations));
    }

    // One extra bucket: a blank value dropped afterwards must not shrink the list below the cap.
    private static Aggregation termsAggregation(final String field) {
        return Aggregation.of(a -> a.terms(t -> t
                .field(field)
                .size(FACET_MAX_VALUES + 1)
                .order(List.of(NamedValue.of(COUNT_ORDER, SortOrder.Desc), NamedValue.of(KEY_ORDER, SortOrder.Asc)))));
    }

    private static Map<String, Aggregation> minMaxAggregations(final String field) {
        final Map<String, Aggregation> minMax = new LinkedHashMap<>();
        minMax.put(MIN_SUB_AGG, Aggregation.of(a -> a.min(m -> m.field(field))));
        minMax.put(MAX_SUB_AGG, Aggregation.of(a -> a.max(m -> m.field(field))));
        return minMax;
    }

    private static Map<String, Aggregate> aggregates(final ElasticsearchAggregations esAggregations) {
        final Map<String, Aggregate> aggregates = new LinkedHashMap<>();
        esAggregations.aggregationsAsMap().forEach((name, aggregation) -> aggregates.put(name, aggregation.aggregation().getAggregate()));
        return aggregates;
    }

    private static Optional<Aggregate> filterBucket(final Map<String, Aggregate> results, final String name) {
        return Optional.ofNullable(results.get(name)).filter(Aggregate::isFilter);
    }

    private static Optional<Aggregate> subAggregate(final Map<String, Aggregate> results, final String name, final String subName) {
        return filterBucket(results, name).map(a -> a.filter().aggregations().get(subName));
    }

    // An unmapped field (an attribute no product has yet) answers with no string-terms buckets: empty list.
    private static List<FacetValueDto> termsValues(final Map<String, Aggregate> results, final String name) {
        return subAggregate(results, name, VALUES_SUB_AGG)
                .filter(Aggregate::isSterms)
                .map(a -> a.sterms().buckets().array().stream()
                        .map(bucket -> new FacetValueDto(bucket.key().stringValue(), bucket.docCount()))
                        .filter(value -> StringUtils.hasText(value.getValue()))
                        .sorted(BY_COUNT_THEN_VALUE)
                        .limit(FACET_MAX_VALUES)
                        .toList())
                .orElse(List.of());
    }

    private static RangeFacetDto range(final Map<String, Aggregate> results, final String name) {
        final Double min = subAggregate(results, name, MIN_SUB_AGG).filter(Aggregate::isMin).map(a -> a.min().value()).orElse(null);
        final Double max = subAggregate(results, name, MAX_SUB_AGG).filter(Aggregate::isMax).map(a -> a.max().value()).orElse(null);
        return new RangeFacetDto(finiteOrNull(min), finiteOrNull(max));
    }

    // No matching product: Elasticsearch answers null (or +/-Infinity on some versions), exposed as null.
    private static Double finiteOrNull(final Double value) {
        return value == null || value.isInfinite() || value.isNaN() ? null : value;
    }

    private record SchemaAttributes(List<String> textKeys, List<String> numericKeys) {
    }
}
