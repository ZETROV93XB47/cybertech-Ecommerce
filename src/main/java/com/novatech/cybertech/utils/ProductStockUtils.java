package com.novatech.cybertech.utils;

import com.novatech.cybertech.entities.ProductEntity;

/**
 * Single definition of a product's sellable stock: {@code stock - reservedStock} (units pinned
 * to live order reservations are not sellable), null-safe and never negative.
 */
public final class ProductStockUtils {

    private ProductStockUtils() {
        // utility class — no instantiation
    }

    /** Units that can still be ordered: {@code max(0, stock - reservedStock)}, null counted as 0. */
    public static int availableStock(final ProductEntity product) {
        final int stock = product.getStock() == null ? 0 : product.getStock();
        final int reserved = product.getReservedStock() == null ? 0 : product.getReservedStock();
        return Math.max(0, stock - reserved);
    }

    /** {@code true} when at least one unit can be ordered. */
    public static boolean isAvailable(final ProductEntity product) {
        return availableStock(product) > 0;
    }
}
