package com.novatech.cybertech.utils;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.novatech.cybertech.dto.request.search.ProductSearchRequestDto;
import com.novatech.cybertech.dto.request.search.RangeFilter;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Elasticsearch query fragments built from a {@link ProductSearchRequestDto}, shared by the product
 * search and the catalog facets so both always apply exactly the same criteria.
 *
 * <p>As on {@code /search}, {@code attributes} and {@code numericRanges} only apply when a
 * {@code category} is given: attribute keys are only meaningful within one category.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ProductSearchQueryUtils {

    public static final String BRAND_FIELD = "brand";
    public static final String PRICE_FIELD = "price";
    public static final String CATEGORY_FIELD = "category";
    public static final String NAME_FIELD = "name";
    public static final String DESCRIPTION_FIELD = "description";
    public static final String ATTRIBUTES_PREFIX = "attributes.";
    public static final String KEYWORD_SUFFIX = ".keyword";
    private static final String KEYWORD_FUZZINESS = "AUTO";

    /** Exact-value field of a free-text attribute ({@code attributes.<key>.keyword}). */
    public static String attributeKeywordField(final String key) {
        return ATTRIBUTES_PREFIX + key + KEYWORD_SUFFIX;
    }

    /** Numeric field of an attribute ({@code attributes.<key>}). */
    public static String attributeNumericField(final String key) {
        return ATTRIBUTES_PREFIX + key;
    }

    /** Full-text match of the keyword on name and description, if a keyword is given. */
    public static Optional<Query> keywordQuery(final ProductSearchRequestDto req) {
        if (!StringUtils.hasText(req.getKeyword())) {
            return Optional.empty();
        }
        return Optional.of(Query.of(q -> q.multiMatch(mm -> mm
                .query(req.getKeyword())
                .fields(NAME_FIELD, DESCRIPTION_FIELD)
                .fuzziness(KEYWORD_FUZZINESS))));
    }

    /** Exact category filter, if a category is given. */
    public static Optional<Query> categoryFilter(final ProductSearchRequestDto req) {
        if (req.getCategory() == null) {
            return Optional.empty();
        }
        return Optional.of(Query.of(q -> q.term(t -> t.field(CATEGORY_FIELD).value(req.getCategory()))));
    }

    /** Any-of brand filter, if at least one brand is given. */
    public static Optional<Query> brandsFilter(final ProductSearchRequestDto req) {
        if (req.getBrands() == null || req.getBrands().isEmpty()) {
            return Optional.empty();
        }
        final List<FieldValue> brandValues = req.getBrands().stream()
                .map(Enum::name)
                .map(FieldValue::of)
                .toList();
        return Optional.of(Query.of(q -> q.terms(t -> t.field(BRAND_FIELD).terms(v -> v.value(brandValues)))));
    }

    /** Price range filter, if a bound is given. */
    public static Optional<Query> priceFilter(final ProductSearchRequestDto req) {
        if (req.getPriceMin() == null && req.getPriceMax() == null) {
            return Optional.empty();
        }
        return Optional.of(Query.of(q -> q.range(r -> r.number(n -> {
            n.field(PRICE_FIELD);
            if (req.getPriceMin() != null) n.gte(req.getPriceMin());
            if (req.getPriceMax() != null) n.lte(req.getPriceMax());
            return n;
        }))));
    }

    /**
     * One any-of filter per free-text attribute with at least one value, keyed by attribute.
     * Empty when no category is given.
     */
    public static Map<String, Query> attributeFilters(final ProductSearchRequestDto req) {
        final Map<String, Query> filters = new LinkedHashMap<>();
        if (req.getAttributes() == null || req.getCategory() == null) {
            return filters;
        }
        req.getAttributes().forEach((key, values) -> {
            if (values != null && !values.isEmpty()) {
                final List<FieldValue> fieldValues = values.stream()
                        .map(String::valueOf)
                        .map(FieldValue::of)
                        .toList();
                filters.put(key, Query.of(q -> q.terms(t -> t
                        .field(attributeKeywordField(key))
                        .terms(v -> v.value(fieldValues)))));
            }
        });
        return filters;
    }

    /** One range filter per numeric attribute, keyed by attribute. Empty when no category is given. */
    public static Map<String, Query> numericRangeFilters(final ProductSearchRequestDto req) {
        final Map<String, Query> filters = new LinkedHashMap<>();
        if (req.getNumericRanges() == null || req.getCategory() == null) {
            return filters;
        }
        req.getNumericRanges().forEach((key, range) -> filters.put(key, rangeQuery(attributeNumericField(key), range)));
        return filters;
    }

    private static Query rangeQuery(final String field, final RangeFilter range) {
        return Query.of(q -> q.range(r -> r.number(n -> {
            n.field(field);
            if (range.getMin() != null) n.gte(range.getMin());
            if (range.getMax() != null) n.lte(range.getMax());
            return n;
        })));
    }
}
