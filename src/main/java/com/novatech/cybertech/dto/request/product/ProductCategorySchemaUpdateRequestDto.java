package com.novatech.cybertech.dto.request.product;

/**
 * PATCH payload for the admin product-category-schema endpoint. Every field is optional —
 * {@code null} means "do not change". A non-null {@code jsonSchema} is re-compiled before saving.
 */
public record ProductCategorySchemaUpdateRequestDto(
        String label,
        String jsonSchema,
        Boolean active
) {
}
