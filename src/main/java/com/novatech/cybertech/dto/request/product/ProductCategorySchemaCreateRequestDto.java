package com.novatech.cybertech.dto.request.product;

import jakarta.validation.constraints.NotBlank;

/**
 * Registers a new product category. {@code jsonSchema} must compile as a valid JSON Schema
 * (checked before persisting — see {@code ProductCategorySchemaServiceImp}).
 */
public record ProductCategorySchemaCreateRequestDto(
        @NotBlank(message = "categoryKey cannot be blank") String categoryKey,
        @NotBlank(message = "label cannot be blank") String label,
        @NotBlank(message = "jsonSchema cannot be blank") String jsonSchema
) {
}
