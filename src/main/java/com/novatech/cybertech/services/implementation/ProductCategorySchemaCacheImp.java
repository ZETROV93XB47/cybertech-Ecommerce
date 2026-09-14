package com.novatech.cybertech.services.implementation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.novatech.cybertech.entities.ProductCategorySchemaEntity;
import com.novatech.cybertech.exceptions.InvalidProductCategorySchemaException;
import com.novatech.cybertech.exceptions.UnknownProductCategoryException;
import com.novatech.cybertech.repositories.ProductCategorySchemaRepository;
import com.novatech.cybertech.services.core.ProductCategorySchemaCache;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * See {@link ProductCategorySchemaCache} for the contract's intent.
 *
 * <p>networknt/json-schema-validator carries its own Jackson 2 ({@code com.fasterxml.jackson})
 * {@link JsonNode} API, independent of this project's Jackson 3 ({@code tools.jackson.databind})
 * stack used elsewhere — the plain Jackson 2 {@link ObjectMapper} below exists solely to feed
 * this library, never wired into Spring's bean graph.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductCategorySchemaCacheImp implements ProductCategorySchemaCache {

    private static final JsonSchemaFactory SCHEMA_FACTORY = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
    private static final ObjectMapper JSON_SCHEMA_NODE_MAPPER = new ObjectMapper();
    private static final Duration MISSED_INVALIDATION_SAFETY_NET_TTL = Duration.ofMinutes(20);

    private final ProductCategorySchemaRepository productCategorySchemaRepository;

    private final Cache<String, JsonSchema> cache = Caffeine.newBuilder()
            .expireAfterWrite(MISSED_INVALIDATION_SAFETY_NET_TTL)
            .build();

    @Override
    @PostConstruct
    public void loadAll() {
        productCategorySchemaRepository.findByActiveTrue()
                .forEach(row -> cache.put(row.getCategoryKey(), compile(row.getJsonSchema())));
        log.info("Loaded {} product category schema(s) into cache", cache.estimatedSize());
    }

    @Override
    public JsonSchema get(final String categoryKey) {
        return cache.get(categoryKey, this::loadAndCompile);
    }

    @Override
    public void invalidate(final String categoryKey) {
        cache.invalidate(categoryKey);
    }

    @Override
    public JsonSchema compile(final String jsonSchemaText) {
        try {
            final JsonNode schemaNode = JSON_SCHEMA_NODE_MAPPER.readTree(jsonSchemaText);
            return SCHEMA_FACTORY.getSchema(schemaNode);
        } catch (final Exception e) {
            throw new InvalidProductCategorySchemaException("Malformed JSON Schema: " + e.getMessage(), e);
        }
    }

    private JsonSchema loadAndCompile(final String categoryKey) {
        final ProductCategorySchemaEntity row = productCategorySchemaRepository.findByCategoryKey(categoryKey)
                .filter(ProductCategorySchemaEntity::isActive)
                .orElseThrow(() -> new UnknownProductCategoryException("Unknown product category: " + categoryKey));
        return compile(row.getJsonSchema());
    }
}
