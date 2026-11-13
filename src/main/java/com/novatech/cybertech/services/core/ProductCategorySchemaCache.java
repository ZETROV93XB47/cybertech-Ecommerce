package com.novatech.cybertech.services.core;

import com.networknt.schema.JsonSchema;

/**
 * In-process (Caffeine) cache of compiled per-category {@link JsonSchema}s, hydrated at startup
 * from {@code ProductCategorySchemaEntity} and kept in sync across instances via Redis Pub/Sub
 * ({@code ProductCategorySchemaCacheInvalidationListener}).
 */
public interface ProductCategorySchemaCache {

    /**
     * Resolves the compiled schema for a category. A cache miss lazily reloads from the DB
     * (covers a dropped Pub/Sub message) rather than failing outright.
     *
     * @throws com.novatech.cybertech.exceptions.UnknownProductCategoryException if no active row exists for {@code categoryKey}.
     */
    JsonSchema get(String categoryKey);

    /** Evicts one entry — called on receipt of a Pub/Sub invalidation message, or after an admin write. */
    void invalidate(String categoryKey);

    /** Hydrates the cache from every active row. Called once at startup. */
    void loadAll();

    /**
     * Compiles raw JSON Schema text without touching the cache — used by the admin service to
     * fail fast on a malformed schema before it is ever persisted.
     *
     * @throws com.novatech.cybertech.exceptions.InvalidProductCategorySchemaException if the text does not compile as a JSON Schema.
     */
    JsonSchema compile(String jsonSchemaText);
}
