package com.novatech.cybertech.dto.response.product;

import lombok.Builder;

import java.util.UUID;

/**
 * Live availability of one product: {@code stock - reservedStock}, i.e. the units that can still
 * be reserved by a new order right now. Kept out of {@link ProductResponseDto} on purpose — the
 * product payload is cache-friendly (search index, front ISR pages), availability is not.
 */
@Builder
public record ProductAvailabilityDto(
        UUID productUuid,
        int availableStock,
        boolean inStock
) {
}
