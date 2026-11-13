package com.novatech.cybertech.exceptions;

import java.util.UUID;

/**
 * One product line that cannot be served: {@code requested} units asked, only {@code available}
 * free ({@code stock - reservedStock}, floored at 0). Carried by {@link NotEnoughStockException}.
 */
public record StockShortage(UUID productUuid, int requested, int available) {

    /** Builds the shortage from the raw product counters, never reporting a negative availability. */
    public static StockShortage of(final UUID productUuid, final int requested, final int stock, final int reservedStock) {
        return new StockShortage(productUuid, requested, Math.max(0, stock - reservedStock));
    }
}
