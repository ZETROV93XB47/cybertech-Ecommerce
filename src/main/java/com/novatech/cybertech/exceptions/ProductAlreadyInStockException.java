package com.novatech.cybertech.exceptions;

/**
 * A back-in-stock alert was requested for a product that can already be ordered. Mapped to 409 {@code PRODUCT_ALREADY_IN_STOCK}.
 */
public class ProductAlreadyInStockException extends RuntimeException {
    public ProductAlreadyInStockException(String message) {
        super(message);
    }

    public ProductAlreadyInStockException(String message, Throwable cause) {
        super(message, cause);
    }
}
