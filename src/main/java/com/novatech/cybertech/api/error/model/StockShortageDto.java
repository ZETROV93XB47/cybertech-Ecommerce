package com.novatech.cybertech.api.error.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.UUID;

/**
 * One line rejected with {@code NOT_ENOUGH_STOCK}, inside {@link ErrorDetailsDto#getItems()}.
 * Product UUID and quantities only — no personal data.
 */
@Getter
@Builder
@AllArgsConstructor
@EqualsAndHashCode
@Schema(description = "A product line whose quantity exceeds the available stock")
public class StockShortageDto {

    @Schema(description = "UUID of the product in shortage", example = "0199c6a1-3f2b-7c4e-9d10-5b8a2e4f6c01")
    private final UUID productUuid;

    @Schema(description = "Quantity asked for this product by the request", example = "3")
    private final int requested;

    @Schema(description = "Quantity that can still be ordered (stock - reserved stock, never negative)", example = "1", minimum = "0")
    private final int available;
}
