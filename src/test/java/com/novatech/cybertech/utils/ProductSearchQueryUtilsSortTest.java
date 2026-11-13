package com.novatech.cybertech.utils;

import co.elastic.clients.elasticsearch._types.FieldSort;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import com.novatech.cybertech.dto.request.search.ProductSearchRequestDto;
import com.novatech.cybertech.entities.enums.ProductSearchSort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ProductSearchQueryUtils.sortOptions — translation of ProductSearchRequestDto.sort")
class ProductSearchQueryUtilsSortTest {

    private static List<SortOptions> sortOf(final ProductSearchSort sort) {
        return ProductSearchQueryUtils.sortOptions(ProductSearchRequestDto.builder().sort(sort).build());
    }

    private static void assertField(final SortOptions option, final String field, final SortOrder order) {
        assertThat(option.isField()).isTrue();
        final FieldSort fieldSort = option.field();
        assertThat(fieldSort.field()).isEqualTo(field);
        assertThat(fieldSort.order()).isEqualTo(order);
    }

    @Test
    @DisplayName("absent sort = RELEVANCE: _score desc, then uuid asc")
    void absentSortIsRelevance() {
        final List<SortOptions> options = ProductSearchQueryUtils.sortOptions(ProductSearchRequestDto.builder().build());

        assertThat(options).hasSize(2);
        assertThat(options.getFirst().isScore()).isTrue();
        assertThat(options.getFirst().score().order()).isEqualTo(SortOrder.Desc);
        assertField(options.get(1), "uuid", SortOrder.Asc);
        // SortOptions has no equals(): compared through their JSON form.
        assertThat(options.toString()).isEqualTo(sortOf(ProductSearchSort.RELEVANCE).toString());
    }

    @Test
    @DisplayName("NAME_ASC sorts on the normalized name.keyword sub-field")
    void nameAsc() {
        final List<SortOptions> options = sortOf(ProductSearchSort.NAME_ASC);

        assertThat(options).hasSize(2);
        assertField(options.getFirst(), "name.keyword", SortOrder.Asc);
    }

    @Test
    @DisplayName("PRICE_ASC / PRICE_DESC sort on price")
    void price() {
        assertField(sortOf(ProductSearchSort.PRICE_ASC).getFirst(), "price", SortOrder.Asc);
        assertField(sortOf(ProductSearchSort.PRICE_DESC).getFirst(), "price", SortOrder.Desc);
    }

    @Test
    @DisplayName("RATING_DESC: averageRating desc with unrated products last, then reviewCount desc")
    void ratingDesc() {
        final List<SortOptions> options = sortOf(ProductSearchSort.RATING_DESC);

        assertThat(options).hasSize(3);
        assertField(options.getFirst(), "averageRating", SortOrder.Desc);
        assertThat(options.getFirst().field().missing().stringValue()).isEqualTo("_last");
        assertField(options.get(1), "reviewCount", SortOrder.Desc);
    }

    @ParameterizedTest(name = "{0} ends with the uuid tie-break")
    @EnumSource(ProductSearchSort.class)
    void everySortEndsWithUuidTieBreak(final ProductSearchSort sort) {
        assertField(sortOf(sort).getLast(), "uuid", SortOrder.Asc);
    }
}
