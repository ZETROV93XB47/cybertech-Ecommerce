package com.novatech.cybertech.entities.enums;

/** Lifecycle of a back-in-stock alert ({@code StockAlertEntity}). */
public enum StockAlertStatus {
    /** Waiting for the product to be available again. */
    PENDING,
    /** The product came back and the user was notified: the alert is over. */
    NOTIFIED,
    /** The user removed the alert. */
    CANCELLED,
    /** Never triggered within {@code cybertech.stock-alert.expire-after-days}. */
    EXPIRED
}
