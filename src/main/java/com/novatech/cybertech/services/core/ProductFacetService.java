package com.novatech.cybertech.services.core;

import com.novatech.cybertech.dto.request.search.ProductSearchRequestDto;
import com.novatech.cybertech.dto.response.search.ProductFacetsResponseDto;

/**
 * Catalog facets: the filter values present among the products a search matches, with their counts,
 * so the storefront offers only filters that lead somewhere.
 */
public interface ProductFacetService {

    /**
     * Facets of the products matching {@code request} ({@code page} and {@code size} are ignored).
     * Each facet is counted under every active filter except its own; keyword and category always apply.
     * Attribute facets are listed only when a category is given, with keys read from its JSON Schema.
     *
     * @throws com.novatech.cybertech.exceptions.UnknownProductCategoryException if the category has no schema.
     */
    ProductFacetsResponseDto getFacets(ProductSearchRequestDto request);
}
