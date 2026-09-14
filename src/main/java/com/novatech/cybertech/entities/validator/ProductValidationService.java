package com.novatech.cybertech.entities.validator;

import com.networknt.schema.JsonSchema;
import com.networknt.schema.ValidationMessage;
import com.novatech.cybertech.exceptions.ProductConstraintsViolationException;
import com.novatech.cybertech.services.core.ProductCategorySchemaCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validates a product's {@code attributes} payload against its category's JSON Schema
 * (registered via the admin API, compiled and cached by {@link ProductCategorySchemaCache}) —
 * replaces the previous hardcoded {@code Category} enum switch + fixed Jakarta-validated
 * records, so a new category (or a changed validation rule) needs no Java code change.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductValidationService {

    /**
     * networknt/json-schema-validator ships its own Jackson 2 ({@code com.fasterxml.jackson})
     * {@link com.fasterxml.jackson.databind.JsonNode} API, independent of this project's
     * Jackson 3 ({@code tools.jackson.databind}) stack used everywhere else — this mapper exists
     * solely to re-parse the JSON this class already serialized, never wired into Spring's bean graph.
     */
    private static final com.fasterxml.jackson.databind.ObjectMapper JSON_SCHEMA_NODE_MAPPER = new com.fasterxml.jackson.databind.ObjectMapper();

    private final ProductCategorySchemaCache productCategorySchemaCache;
    private final ObjectMapper objectMapper;

    public void validateAttributes(final String categoryKey, final Map<String, Object> attributes) {
        final JsonSchema schema = productCategorySchemaCache.get(categoryKey);
        final com.fasterxml.jackson.databind.JsonNode node = toJackson2Node(attributes);

        final Set<ValidationMessage> violations = schema.validate(node);
        if (!violations.isEmpty()) {
            final String message = violations.stream()
                    .map(ValidationMessage::getMessage)
                    .collect(Collectors.joining("; "));
            log.info("Product attributes for category {} failed schema validation: {}", categoryKey, message);
            throw new ProductConstraintsViolationException(message, violations);
        }
    }

    private com.fasterxml.jackson.databind.JsonNode toJackson2Node(final Map<String, Object> attributes) {
        final String json = objectMapper.writeValueAsString(attributes);
        try {
            return JSON_SCHEMA_NODE_MAPPER.readTree(json);
        } catch (final java.io.IOException e) {
            // Should never happen — we just serialized this ourselves with a working mapper.
            throw new IllegalStateException("Unable to bridge product attributes to a JSON Schema node", e);
        }
    }
}
