package com.novatech.cybertech.dto.response.product;

import lombok.Builder;

import java.util.UUID;

/**
 * Read-side projection of a {@code ProductCategorySchemaEntity}.
 */
@Builder
public record ProductCategorySchemaResponseDto(
        UUID uuid,
        String categoryKey,
        String label,
        String jsonSchema,
        boolean active
) {
}
