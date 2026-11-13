package com.novatech.cybertech.services.implementation.catalog;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.networknt.schema.JsonSchema;
import com.novatech.cybertech.dto.request.search.ProductSearchRequestDto;
import com.novatech.cybertech.dto.request.search.RangeFilter;
import com.novatech.cybertech.dto.response.search.FacetValueDto;
import com.novatech.cybertech.dto.response.search.ProductFacetsResponseDto;
import com.novatech.cybertech.dto.response.search.RangeFacetDto;
import com.novatech.cybertech.entities.document.ProductDocument;
import com.novatech.cybertech.entities.enums.Brand;
import com.novatech.cybertech.exceptions.UnknownProductCategoryException;
import com.novatech.cybertech.repositories.ProductCategorySchemaRepository;
import com.novatech.cybertech.services.core.ProductCategorySchemaCache;
import com.novatech.cybertech.services.implementation.ProductCategorySchemaCacheImp;
import com.novatech.cybertech.services.implementation.ProductFacetServiceImp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static com.novatech.cybertech.constants.CyberTechAppConstants.FACET_MAX_VALUES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Mockito unit tests for {@link ProductFacetServiceImp}: the single Elasticsearch request it builds
 * (disjunctive filter aggregations, schema-driven attribute facets, size 0) and how it reads the
 * aggregations back (sorting, cap, blank values, missing or unmapped facets).
 */
@ExtendWith(MockitoExtension.class)
class ProductFacetServiceImpTest {

    private static final String COMPUTER = "COMPUTER";
    private static final String COMPUTER_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "cpu": {"type": "string", "minLength": 1},
                "gpu": {"type": "string", "minLength": 1},
                "ram": {"type": "integer", "minimum": 8},
                "os": {"type": "string", "minLength": 1},
                "weight": {"type": "number"}
              }
            }
            """;
    // Aggregation names the service gives each facet: text attributes and numeric attributes are
    // named by their position among the schema's text / numeric keys.
    private static final String ALL = "all";
    private static final String BRANDS = "brands";
    private static final String PRICE = "price";
    private static final String CPU = "attr_0";
    private static final String GPU = "attr_1";
    private static final String OS = "attr_2";
    private static final String RAM = "num_0";
    private static final String WEIGHT = "num_1";

    private static final ProductCategorySchemaCacheImp SCHEMA_COMPILER =
            new ProductCategorySchemaCacheImp(mock(ProductCategorySchemaRepository.class));

    @Mock ElasticsearchOperations elasticsearchOperations;
    @Mock ProductCategorySchemaCache productCategorySchemaCache;

    private ProductFacetServiceImp service;

    @BeforeEach
    void setUp() {
        service = new ProductFacetServiceImp(elasticsearchOperations, productCategorySchemaCache);
    }

    // ---------------------------------------------------------------------------------------------
    // Fixtures
    // ---------------------------------------------------------------------------------------------

    private void stubSchema(final String category, final String schemaJson) {
        final JsonSchema schema = SCHEMA_COMPILER.compile(schemaJson);
        when(productCategorySchemaCache.get(category)).thenReturn(schema);
    }

    @SuppressWarnings("unchecked")
    private void stubAggregations(final Map<String, Aggregate> aggregates) {
        final SearchHits<ProductDocument> hits = mock(SearchHits.class);
        doReturn(new ElasticsearchAggregations(aggregates)).when(hits).getAggregations();
        when(elasticsearchOperations.search(any(NativeQuery.class), eq(ProductDocument.class))).thenReturn(hits);
    }

    private NativeQuery captureQuery() {
        final ArgumentCaptor<NativeQuery> captor = ArgumentCaptor.forClass(NativeQuery.class);
        verify(elasticsearchOperations).search(captor.capture(), eq(ProductDocument.class));
        return captor.getValue();
    }

    private static Aggregate filterAgg(final long docCount, final Map<String, Aggregate> subAggregates) {
        return Aggregate.of(a -> a.filter(f -> f.docCount(docCount).aggregations(subAggregates)));
    }

    private static Aggregate termsFacet(final long docCount, final List<StringTermsBucket> buckets) {
        final Aggregate terms = Aggregate.of(a -> a.sterms(s -> s
                .sumOtherDocCount(0L)
                .docCountErrorUpperBound(0L)
                .buckets(b -> b.array(buckets))));
        return filterAgg(docCount, Map.of("values", terms));
    }

    private static Aggregate rangeFacet(final Double min, final Double max) {
        return filterAgg(1, Map.of(
                "min", Aggregate.of(a -> a.min(m -> m.value(min))),
                "max", Aggregate.of(a -> a.max(m -> m.value(max)))));
    }

    private static StringTermsBucket bucket(final String key, final long count) {
        return StringTermsBucket.of(b -> b.key(key).docCount(count));
    }

    private static List<Query> filtersOf(final Aggregation aggregation) {
        final Query filter = aggregation.filter();
        return filter.isMatchAll() ? List.of() : filter.bool().filter();
    }

    private static boolean isTermsOn(final Query query, final String field) {
        return query.isTerms() && field.equals(query.terms().field());
    }

    private static boolean isRangeOn(final Query query, final String field) {
        return query.isRange() && field.equals(query.range().number().field());
    }

    // ---------------------------------------------------------------------------------------------
    @Nested
    @DisplayName("request sent to Elasticsearch")
    class Request {

        @Test
        @DisplayName("one search, no hits, only brand and price facets without a category")
        void noCategory_brandAndPriceFacetsOnly() {
            stubAggregations(Map.of());

            service.getFacets(ProductSearchRequestDto.builder().page(3).size(7).build());

            final NativeQuery query = captureQuery();
            assertThat(query.getMaxResults()).isZero();
            assertThat(query.getAggregations()).containsOnlyKeys(ALL, BRANDS, PRICE);
            assertThat(query.getQuery().bool().must()).isEmpty();
            assertThat(query.getQuery().bool().filter()).isEmpty();
            assertThat(query.getAggregations().get(BRANDS).filter().isMatchAll()).isTrue();
            final Aggregation brandValues = query.getAggregations().get(BRANDS).aggregations().get("values");
            assertThat(brandValues.terms().field()).isEqualTo("brand");
            // One spare bucket so a dropped blank value cannot shrink the list below the cap.
            assertThat(brandValues.terms().size()).isEqualTo(FACET_MAX_VALUES + 1);
            verifyNoInteractions(productCategorySchemaCache);
        }

        @Test
        @DisplayName("keyword and category stay in the shared query, applied to every facet")
        void keywordAndCategory_inSharedQuery() {
            stubSchema(COMPUTER, COMPUTER_SCHEMA);
            stubAggregations(Map.of());

            service.getFacets(ProductSearchRequestDto.builder().category(COMPUTER).keyword("omen").build());

            final Query bool = captureQuery().getQuery();
            assertThat(bool.bool().must()).singleElement().satisfies(q -> assertThat(q.isMultiMatch()).isTrue());
            assertThat(bool.bool().filter()).singleElement()
                    .satisfies(q -> assertThat(q.term().value().stringValue()).isEqualTo(COMPUTER));
        }

        @Test
        @DisplayName("attribute facets come from the category JSON Schema: terms on .keyword for strings, min/max for numbers")
        void attributeFacets_fromSchema() {
            stubSchema(COMPUTER, COMPUTER_SCHEMA);
            stubAggregations(Map.of());

            service.getFacets(ProductSearchRequestDto.builder().category(COMPUTER).build());

            final Map<String, Aggregation> aggregations = captureQuery().getAggregations();
            assertThat(aggregations).containsOnlyKeys(ALL, BRANDS, PRICE, CPU, GPU, OS, RAM, WEIGHT);
            assertThat(aggregations.get(CPU).aggregations().get("values").terms().field()).isEqualTo("attributes.cpu.keyword");
            assertThat(aggregations.get(OS).aggregations().get("values").terms().field()).isEqualTo("attributes.os.keyword");
            assertThat(aggregations.get(RAM).aggregations().get("min").min().field()).isEqualTo("attributes.ram");
            assertThat(aggregations.get(WEIGHT).aggregations().get("max").max().field()).isEqualTo("attributes.weight");
        }

        @Test
        @DisplayName("disjunctive: each facet carries every active filter except its own")
        void disjunctiveFilters() {
            stubSchema(COMPUTER, COMPUTER_SCHEMA);
            stubAggregations(Map.of());
            final RangeFilter ramRange = new RangeFilter();
            ramRange.setMin(16.0);

            service.getFacets(ProductSearchRequestDto.builder()
                    .category(COMPUTER)
                    .brands(List.of(Brand.LENOVO))
                    .priceMin(500.0)
                    .attributes(Map.of("cpu", List.of("Intel Core i7")))
                    .numericRanges(Map.of("ram", ramRange))
                    .build());

            final Map<String, Aggregation> aggregations = captureQuery().getAggregations();
            assertThat(filtersOf(aggregations.get(ALL))).hasSize(4);

            assertThat(filtersOf(aggregations.get(BRANDS))).hasSize(3).noneMatch(q -> isTermsOn(q, "brand"));
            assertThat(filtersOf(aggregations.get(PRICE))).hasSize(3).noneMatch(q -> isRangeOn(q, "price"));
            assertThat(filtersOf(aggregations.get(CPU))).hasSize(3)
                    .noneMatch(q -> isTermsOn(q, "attributes.cpu.keyword"))
                    .anyMatch(q -> isTermsOn(q, "brand"));
            assertThat(filtersOf(aggregations.get(GPU))).hasSize(4);
            assertThat(filtersOf(aggregations.get(RAM))).hasSize(3).noneMatch(q -> isRangeOn(q, "attributes.ram"));
            assertThat(filtersOf(aggregations.get(WEIGHT))).hasSize(4);
        }

        @Test
        @DisplayName("a filter on an attribute the schema does not declare restricts every facet")
        void undeclaredAttributeFilter_restrictsEveryFacet() {
            stubSchema(COMPUTER, COMPUTER_SCHEMA);
            stubAggregations(Map.of());

            service.getFacets(ProductSearchRequestDto.builder()
                    .category(COMPUTER)
                    .attributes(Map.of("colour", List.of("black")))
                    .build());

            captureQuery().getAggregations().values()
                    .forEach(aggregation -> assertThat(filtersOf(aggregation)).singleElement()
                            .satisfies(q -> assertThat(isTermsOn(q, "attributes.colour.keyword")).isTrue()));
        }

        @Test
        @DisplayName("attributes and numeric ranges are ignored without a category, as on /search")
        void attributeFiltersIgnoredWithoutCategory() {
            stubAggregations(Map.of());

            service.getFacets(ProductSearchRequestDto.builder()
                    .attributes(Map.of("cpu", List.of("Intel Core i7")))
                    .build());

            assertThat(captureQuery().getAggregations().get(ALL).filter().isMatchAll()).isTrue();
        }

        @Test
        @DisplayName("a nullable type counts as its non-null type; boolean, object and untyped properties are no facet")
        void schemaTypes() {
            stubSchema(COMPUTER, """
                    {"type": "object", "properties": {
                      "cpu": {"type": ["null", "string"]},
                      "touch": {"type": "boolean"},
                      "ports": {"type": "array"},
                      "misc": {},
                      "ram": {"type": ["integer", "null"]}
                    }}
                    """);
            stubAggregations(Map.of());

            service.getFacets(ProductSearchRequestDto.builder().category(COMPUTER).build());

            assertThat(captureQuery().getAggregations()).containsOnlyKeys(ALL, BRANDS, PRICE, "attr_0", "num_0");
        }

        @Test
        @DisplayName("unknown category: UnknownProductCategoryException, no Elasticsearch call")
        void unknownCategory() {
            when(productCategorySchemaCache.get("TOASTER")).thenThrow(new UnknownProductCategoryException("Unknown product category: TOASTER"));

            assertThatThrownBy(() -> service.getFacets(ProductSearchRequestDto.builder().category("TOASTER").build()))
                    .isInstanceOf(UnknownProductCategoryException.class);
            verifyNoInteractions(elasticsearchOperations);
        }
    }

    // ---------------------------------------------------------------------------------------------
    @Nested
    @DisplayName("response read from the aggregations")
    class Response {

        @Test
        @DisplayName("totals, brands by count desc then name, price bounds; no attribute sections without a category")
        void noCategory_response() {
            final Map<String, Aggregate> aggregates = new HashMap<>();
            aggregates.put(ALL, filterAgg(5, Map.of()));
            aggregates.put(BRANDS, termsFacet(5, List.of(bucket("LENOVO", 1), bucket("DELL", 2), bucket("HP", 1), bucket("SAMSUNG", 1))));
            aggregates.put(PRICE, rangeFacet(349.0, 3299.0));
            stubAggregations(aggregates);

            final ProductFacetsResponseDto facets = service.getFacets(ProductSearchRequestDto.builder().build());

            assertThat(facets.getTotalHits()).isEqualTo(5);
            assertThat(facets.getBrands()).containsExactly(
                    new FacetValueDto("DELL", 2), new FacetValueDto("HP", 1),
                    new FacetValueDto("LENOVO", 1), new FacetValueDto("SAMSUNG", 1));
            assertThat(facets.getPrice()).isEqualTo(new RangeFacetDto(349.0, 3299.0));
            assertThat(facets.getAttributes()).isNull();
            assertThat(facets.getNumericAttributes()).isNull();
        }

        @Test
        @DisplayName("every schema attribute is a key, in schema order; missing or unmapped facets are empty")
        void category_response() {
            stubSchema(COMPUTER, COMPUTER_SCHEMA);
            final Map<String, Aggregate> aggregates = new HashMap<>();
            aggregates.put(ALL, filterAgg(3, Map.of()));
            aggregates.put(BRANDS, termsFacet(3, List.of(bucket("DELL", 3))));
            aggregates.put(PRICE, rangeFacet(800.0, 2000.0));
            aggregates.put(CPU, termsFacet(3, List.of(bucket("Intel i7", 2), bucket("AMD Ryzen 7", 1))));
            // An attribute no product has yet: Elasticsearch answers with unmapped terms.
            aggregates.put(GPU, filterAgg(3, Map.of("values", Aggregate.of(a -> a.umterms(u -> u
                    .sumOtherDocCount(0L).docCountErrorUpperBound(0L).buckets(b -> b.array(List.of())))))));
            aggregates.put(RAM, rangeFacet(8.0, 64.0));
            aggregates.put(WEIGHT, rangeFacet(null, null));
            stubAggregations(aggregates);

            final ProductFacetsResponseDto facets = service.getFacets(ProductSearchRequestDto.builder().category(COMPUTER).build());

            assertThat(facets.getAttributes()).containsOnlyKeys("cpu", "gpu", "os");
            assertThat(facets.getAttributes().keySet()).containsExactly("cpu", "gpu", "os");
            assertThat(facets.getAttributes().get("cpu")).containsExactly(new FacetValueDto("Intel i7", 2), new FacetValueDto("AMD Ryzen 7", 1));
            assertThat(facets.getAttributes().get("gpu")).isEmpty();
            assertThat(facets.getAttributes().get("os")).isEmpty();
            assertThat(facets.getNumericAttributes().keySet()).containsExactly("ram", "weight");
            assertThat(facets.getNumericAttributes().get("ram")).isEqualTo(new RangeFacetDto(8.0, 64.0));
            assertThat(facets.getNumericAttributes().get("weight")).isEqualTo(new RangeFacetDto(null, null));
        }

        @Test
        @DisplayName("blank values are skipped and the list is capped at FACET_MAX_VALUES")
        void blankSkipped_andCapped() {
            final List<StringTermsBucket> buckets = new java.util.ArrayList<>();
            buckets.add(bucket("", 100));
            buckets.add(bucket("   ", 90));
            IntStream.rangeClosed(1, FACET_MAX_VALUES + 1)
                    .forEach(i -> buckets.add(bucket("value-%03d".formatted(i), 1000 - i)));
            stubAggregations(Map.of(BRANDS, termsFacet(1, buckets)));

            final List<FacetValueDto> brands = service.getFacets(ProductSearchRequestDto.builder().build()).getBrands();

            assertThat(brands).hasSize(FACET_MAX_VALUES);
            assertThat(brands).extracting(FacetValueDto::getValue)
                    .doesNotContain("", "   ", "value-%03d".formatted(FACET_MAX_VALUES + 1))
                    .startsWith("value-001");
        }

        @Test
        @DisplayName("no aggregation back: zero hits, empty brands, null price bounds")
        void noAggregations() {
            stubAggregations(Map.of());

            final ProductFacetsResponseDto facets = service.getFacets(ProductSearchRequestDto.builder().build());

            assertThat(facets.getTotalHits()).isZero();
            assertThat(facets.getBrands()).isEmpty();
            assertThat(facets.getPrice()).isEqualTo(new RangeFacetDto(null, null));
        }

        @Test
        @DisplayName("infinite bounds (no match on some versions) are exposed as null")
        void infiniteBounds_areNull() {
            stubAggregations(Map.of(PRICE, rangeFacet(Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)));

            assertThat(service.getFacets(ProductSearchRequestDto.builder().build()).getPrice())
                    .isEqualTo(new RangeFacetDto(null, null));
        }
    }
}
