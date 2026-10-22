package com.novatech.cybertech.dto.response.product;

import lombok.Builder;

/**
 * Public, lightweight projection of an active {@code ProductCategorySchemaEntity} — just what a
 * client needs to list the catalogue's categories (menus, filters), without the JSON Schema.
 */
@Builder
public record ProductCategoryResponseDto(
        String categoryKey,
        String label
) {
}
