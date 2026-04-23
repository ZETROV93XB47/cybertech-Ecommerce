package com.novatech.cybertech.services.implementation.catalog;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.novatech.cybertech.dto.request.search.ProductSearchRequestDto;
import com.novatech.cybertech.dto.request.search.RangeFilter;
import com.novatech.cybertech.entities.document.ProductDocument;
import com.novatech.cybertech.entities.enums.Brand;
import com.novatech.cybertech.entities.enums.Category;
import com.novatech.cybertech.services.implementation.ProductSearchServiceImp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mockito unit tests for {@link ProductSearchServiceImp}.
 *
 * <p>SA-W3.5 wave — services/catalog. Captures the {@link NativeQuery} sent to
 * {@link ElasticsearchOperations} and asserts the BoolQuery filter / must clause
 * counts match the requested DTO. BUG-181 (Sort dropped) is pinned.
 */
@ExtendWith(MockitoExtension.class)
class ProductSearchServiceImpTest {

    @Mock ElasticsearchOperations elasticsearchOperations;

    private ProductSearchServiceImp service;

    @BeforeEach
    void setUp() {
        service = new ProductSearchServiceImp(elasticsearchOperations);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void stubEmptyHits() {
        SearchHits hits = mock(SearchHits.class);
        when(hits.getSearchHits()).thenReturn(List.of());
        when(hits.getTotalHits()).thenReturn(0L);
        when(elasticsearchOperations.search(any(NativeQuery.class), eq(ProductDocument.class)))
                .thenReturn(hits);
    }

    @SuppressWarnings({"unchecked"})
    private SearchHit<ProductDocument> oneHit(ProductDocument doc) {
        SearchHit<ProductDocument> h = mock(SearchHit.class);
        when(h.getContent()).thenReturn(doc);
        return h;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void stubHits(List<ProductDocument> docs, long total) {
        SearchHits hits = mock(SearchHits.class);
        List<SearchHit<ProductDocument>> wrapped = new ArrayList<>();
        for (ProductDocument d : docs) wrapped.add(oneHit(d));
        when(hits.getSearchHits()).thenReturn((List) wrapped);
        when(hits.getTotalHits()).thenReturn(total);
        when(elasticsearchOperations.search(any(NativeQuery.class), eq(ProductDocument.class)))
                .thenReturn(hits);
    }

    private NativeQuery captureQuery() {
        ArgumentCaptor<NativeQuery> captor = ArgumentCaptor.forClass(NativeQuery.class);
        verify(elasticsearchOperations).search(captor.capture(), eq(ProductDocument.class));
        return captor.getValue();
    }

    private BoolQuery extractBool(NativeQuery nq) {
        Query q = nq.getQuery();
        assertThat(q).isNotNull();
        assertThat(q.isBool()).isTrue();
        return q.bool();
    }

    // -----------------------------------------------------------------
    @Nested
    @DisplayName("category-only request")
    class CategoryOnly {

        @Test
        @DisplayName("category filter alone produces one filter, zero musts")
        void categoryOnly_oneFilter() {
            ProductSearchRequestDto req = ProductSearchRequestDto.builder()
                    .category(Category.COMPUTER).page(0).size(10).build();
            stubEmptyHits();

            Page<ProductDocument> result = service.search(req);

            assertThat(result.getTotalElements()).isZero();
            BoolQuery bool = extractBool(captureQuery());
            assertThat(bool.filter()).hasSize(1);
            assertThat(bool.must()).isEmpty();
            assertThat(bool.filter().get(0).isTerm()).isTrue();
            assertThat(bool.filter().get(0).term().field()).isEqualTo("category");
            assertThat(bool.filter().get(0).term().value().stringValue()).isEqualTo("COMPUTER");
        }
    }

    // -----------------------------------------------------------------
    @Nested
    @DisplayName("brand filter")
    class BrandFilter {

        @Test
        @DisplayName("single brand emits a terms filter")
        void singleBrand_termsFilter() {
            ProductSearchRequestDto req = ProductSearchRequestDto.builder()
                    .category(Category.COMPUTER).brands(List.of(Brand.DELL)).page(0).size(10).build();
            stubEmptyHits();

            service.search(req);
            BoolQuery bool = extractBool(captureQuery());

            assertThat(bool.filter()).hasSize(2); // category + brand terms
            boolean hasBrandTerms = bool.filter().stream().anyMatch(f ->
                    f.isTerms() && "brand".equals(f.terms().field()));
            assertThat(hasBrandTerms).isTrue();
        }

        @Test
        @DisplayName("multiple brands collapsed into single terms filter")
        void multipleBrands_oneTermsFilter() {
            ProductSearchRequestDto req = ProductSearchRequestDto.builder()
                    .category(Category.COMPUTER).brands(List.of(Brand.DELL, Brand.HP, Brand.ASUS)).page(0).size(10).build();
            stubEmptyHits();

            service.search(req);
            BoolQuery bool = extractBool(captureQuery());

            long termsCount = bool.filter().stream().filter(Query::isTerms).count();
            assertThat(termsCount).isEqualTo(1);
        }

        @Test
        @DisplayName("empty brand list produces no brand filter")
        void emptyBrand_noFilter() {
            ProductSearchRequestDto req = ProductSearchRequestDto.builder()
                    .category(Category.COMPUTER).brands(List.of()).page(0).size(10).build();
            stubEmptyHits();

            service.search(req);
            BoolQuery bool = extractBool(captureQuery());
            // only category
            assertThat(bool.filter()).hasSize(1);
        }
    }

    // -----------------------------------------------------------------
    @Nested
    @DisplayName("price range filter")
    class PriceRange {

        @Test
        @DisplayName("priceMin only emits a range filter on price")
        void priceMinOnly_rangeFilter() {
            ProductSearchRequestDto req = ProductSearchRequestDto.builder()
                    .category(Category.COMPUTER).priceMin(100.0).page(0).size(10).build();
            stubEmptyHits();

            service.search(req);
            BoolQuery bool = extractBool(captureQuery());
            long rangeCount = bool.filter().stream().filter(Query::isRange).count();
            assertThat(rangeCount).isEqualTo(1);
        }

        @Test
        @DisplayName("priceMax only emits a range filter on price")
        void priceMaxOnly_rangeFilter() {
            ProductSearchRequestDto req = ProductSearchRequestDto.builder()
                    .category(Category.COMPUTER).priceMax(500.0).page(0).size(10).build();
            stubEmptyHits();

            service.search(req);
            BoolQuery bool = extractBool(captureQuery());
            long rangeCount = bool.filter().stream().filter(Query::isRange).count();
            assertThat(rangeCount).isEqualTo(1);
        }

        @Test
        @DisplayName("priceMin + priceMax both folded into one range filter")
        void priceRange_both_oneRange() {
            ProductSearchRequestDto req = ProductSearchRequestDto.builder()
                    .category(Category.COMPUTER).priceMin(100.0).priceMax(500.0).page(0).size(10).build();
            stubEmptyHits();

            service.search(req);
            BoolQuery bool = extractBool(captureQuery());
            long rangeCount = bool.filter().stream().filter(Query::isRange).count();
            assertThat(rangeCount).isEqualTo(1);
        }
    }

    // -----------------------------------------------------------------
    @Nested
    @DisplayName("attributes filter")
    class AttributesFilter {

        @Test
        @DisplayName("attribute key with values emits a terms filter under attributes.<CAT>.key.keyword")
        void attribute_termsFilter() {
            ProductSearchRequestDto req = ProductSearchRequestDto.builder()
                    .category(Category.COMPUTER)
                    .attributes(Map.of("ram", List.of("16GB", "32GB")))
                    .page(0).size(10).build();
            stubEmptyHits();

            service.search(req);
            BoolQuery bool = extractBool(captureQuery());
            boolean hasRam = bool.filter().stream().anyMatch(f ->
                    f.isTerms() && "attributes.COMPUTER.ram.keyword".equals(f.terms().field()));
            assertThat(hasRam).isTrue();
        }

        @Test
        @DisplayName("attribute with empty value list is skipped")
        void attribute_emptyValues_skipped() {
            ProductSearchRequestDto req = ProductSearchRequestDto.builder()
                    .category(Category.COMPUTER)
                    .attributes(Map.of("ram", List.of()))
                    .page(0).size(10).build();
            stubEmptyHits();

            service.search(req);
            BoolQuery bool = extractBool(captureQuery());
            assertThat(bool.filter()).hasSize(1); // only category, no terms for empty list
        }

        @Test
        @DisplayName("attributes without category attached produces no attribute filter")
        void attributesWithoutCategory_noFilter() {
            // category is required by validation, but we set null to confirm guard
            ProductSearchRequestDto req = ProductSearchRequestDto.builder()
                    .category(null).attributes(Map.of("ram", List.of("32GB"))).page(0).size(10).build();
            stubEmptyHits();

            service.search(req);
            BoolQuery bool = extractBool(captureQuery());
            assertThat(bool.filter()).isEmpty();
        }
    }

    // -----------------------------------------------------------------
    @Nested
    @DisplayName("numeric ranges filter")
    class NumericRanges {

        @Test
        @DisplayName("numeric ram range >=32 emits a range filter under attributes.<CAT>.ram")
        void numericRange_emitsRangeFilter() {
            ProductSearchRequestDto req = ProductSearchRequestDto.builder()
                    .category(Category.COMPUTER)
                    .numericRanges(Map.of("ram", new RangeFilter(32.0, null)))
                    .page(0).size(10).build();
            stubEmptyHits();

            service.search(req);
            BoolQuery bool = extractBool(captureQuery());
            long rangeCount = bool.filter().stream().filter(Query::isRange).count();
            assertThat(rangeCount).isEqualTo(1);
        }

        @Test
        @DisplayName("numeric range with both bounds emits a single range filter")
        void numericRange_bothBounds() {
            ProductSearchRequestDto req = ProductSearchRequestDto.builder()
                    .category(Category.COMPUTER)
                    .numericRanges(Map.of("storage", new RangeFilter(256.0, 1024.0)))
                    .page(0).size(10).build();
            stubEmptyHits();

            service.search(req);
            BoolQuery bool = extractBool(captureQuery());
            long rangeCount = bool.filter().stream().filter(Query::isRange).count();
            assertThat(rangeCount).isEqualTo(1);
        }

        @Test
        @DisplayName("numeric ranges without category are skipped")
        void numericRanges_withoutCategory_skipped() {
            ProductSearchRequestDto req = ProductSearchRequestDto.builder()
                    .category(null).numericRanges(Map.of("ram", new RangeFilter(32.0, null)))
                    .page(0).size(10).build();
            stubEmptyHits();

            service.search(req);
            BoolQuery bool = extractBool(captureQuery());
            assertThat(bool.filter()).isEmpty();
        }
    }

    // -----------------------------------------------------------------
    @Nested
    @DisplayName("keyword search")
    class KeywordSearch {

        @Test
        @DisplayName("keyword text becomes a multi_match must clause on name + description")
        void keyword_multiMatchMust() {
            ProductSearchRequestDto req = ProductSearchRequestDto.builder()
                    .keyword("gaming laptop").category(Category.COMPUTER).page(0).size(10).build();
            stubEmptyHits();

            service.search(req);
            BoolQuery bool = extractBool(captureQuery());
            assertThat(bool.must()).hasSize(1);
            assertThat(bool.must().get(0).isMultiMatch()).isTrue();
            assertThat(bool.must().get(0).multiMatch().query()).isEqualTo("gaming laptop");
            assertThat(bool.must().get(0).multiMatch().fields())
                    .containsExactlyInAnyOrder("name", "description");
        }

        @Test
        @DisplayName("blank keyword produces no must clause")
        void blankKeyword_noMust() {
            ProductSearchRequestDto req = ProductSearchRequestDto.builder()
                    .keyword("   ").category(Category.COMPUTER).page(0).size(10).build();
            stubEmptyHits();

            service.search(req);
            BoolQuery bool = extractBool(captureQuery());
            assertThat(bool.must()).isEmpty();
        }

        @Test
        @DisplayName("null keyword produces no must clause")
        void nullKeyword_noMust() {
            ProductSearchRequestDto req = ProductSearchRequestDto.builder()
                    .keyword(null).category(Category.COMPUTER).page(0).size(10).build();
            stubEmptyHits();

            service.search(req);
            BoolQuery bool = extractBool(captureQuery());
            assertThat(bool.must()).isEmpty();
        }
    }

    // -----------------------------------------------------------------
    @Nested
    @DisplayName("page / pageable")
    class Paging {

        @Test
        @DisplayName("page + size from DTO are forwarded to the NativeQuery pageable")
        void paging_forwarded() {
            ProductSearchRequestDto req = ProductSearchRequestDto.builder()
                    .category(Category.COMPUTER).page(2).size(7).build();
            stubEmptyHits();

            service.search(req);
            NativeQuery nq = captureQuery();
            assertThat(nq.getPageable()).isNotNull();
            assertThat(nq.getPageable().getPageNumber()).isEqualTo(2);
            assertThat(nq.getPageable().getPageSize()).isEqualTo(7);
        }

        @Test
        @DisplayName("BUG-181: PageRequest is built with no Sort — Sort silently dropped (pin)")
        void bug181_sortIsAlwaysUnsorted() {
            ProductSearchRequestDto req = ProductSearchRequestDto.builder()
                    .category(Category.COMPUTER).page(0).size(10).build();
            stubEmptyHits();

            service.search(req);
            NativeQuery nq = captureQuery();
            assertThat(nq.getPageable().getSort().isUnsorted()).isTrue();
        }
    }

    // -----------------------------------------------------------------
    @Nested
    @DisplayName("result assembly")
    class ResultAssembly {

        @Test
        @DisplayName("hits are mapped to PageImpl with totalHits as totalElements")
        void mapsToPageImpl() {
            ProductSearchRequestDto req = ProductSearchRequestDto.builder()
                    .category(Category.COMPUTER).page(0).size(10).build();
            ProductDocument d1 = ProductDocument.builder().id("1").uuid(UUID.randomUUID()).name("X").build();
            ProductDocument d2 = ProductDocument.builder().id("2").uuid(UUID.randomUUID()).name("Y").build();
            stubHits(List.of(d1, d2), 42L);

            Page<ProductDocument> page = service.search(req);

            assertThat(page.getContent()).containsExactly(d1, d2);
            assertThat(page.getTotalElements()).isEqualTo(42L);
        }

        @Test
        @DisplayName("empty results yield empty page")
        void emptyHits_emptyPage() {
            ProductSearchRequestDto req = ProductSearchRequestDto.builder()
                    .category(Category.COMPUTER).page(0).size(10).build();
            stubEmptyHits();

            Page<ProductDocument> page = service.search(req);

            assertThat(page.getContent()).isEmpty();
            assertThat(page.getTotalElements()).isZero();
        }
    }
}
