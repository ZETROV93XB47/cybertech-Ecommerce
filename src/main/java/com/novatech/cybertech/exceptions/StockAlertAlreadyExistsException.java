package com.novatech.cybertech.exceptions;

/**
 * The user already has a pending back-in-stock alert for this product. Mapped to 409 {@code STOCK_ALERT_ALREADY_EXISTS}.
 */
public class StockAlertAlreadyExistsException extends RuntimeException {
    public StockAlertAlreadyExistsException(String message) {
        super(message);
    }

    public StockAlertAlreadyExistsException(String message, Throwable cause) {
        super(message, cause);
    }
}
