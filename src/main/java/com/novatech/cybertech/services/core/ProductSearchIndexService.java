package com.novatech.cybertech.services.core;

import com.novatech.cybertech.entities.ProductEntity;

import java.util.UUID;

/**
 * Single writer of the {@code products} search index: every document it writes carries the
 * product's published-review statistics ({@code averageRating}, {@code reviewCount}), so a product
 * update never wipes them and the "best rated" sort always reads fresh values.
 */
public interface ProductSearchIndexService {

    /** Indexes (creates or replaces) the product's document, ratings included. */
    void index(ProductEntity product);

    /**
     * Rewrites only {@code averageRating} / {@code reviewCount} of an indexed product, after one of
     * its published reviews was created, modified or deleted. No-op if the product is not indexed.
     */
    void refreshRatings(UUID productUuid);

    /** Indexes every product of the database, ratings included. @return the number of documents written. */
    int reindexAll();
}
