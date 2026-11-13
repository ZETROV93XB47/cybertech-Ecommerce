package com.novatech.cybertech.entities.enums;

/**
 * Order of the product search results ({@code POST /product/search}), applied to the whole result
 * set before pagination. Every order ends with a tie-break on the product uuid so pages are stable.
 */
public enum ProductSearchSort {
    /** Elasticsearch relevance score, highest first (the default). */
    RELEVANCE,
    /** Name A → Z, case- and accent-insensitive ("Écran" sorts with the E). */
    NAME_ASC,
    PRICE_ASC,
    PRICE_DESC,
    /** Best average rating first, then most reviews; products without reviews last. */
    RATING_DESC
}
