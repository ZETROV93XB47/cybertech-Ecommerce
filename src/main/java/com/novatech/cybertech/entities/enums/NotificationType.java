package com.novatech.cybertech.entities.enums;

public enum NotificationType {
    ORDER_CONFIRMATION,
    ORDER_UPDATE,
    SHIPPING_CONFIRMATION,
    PAYMENT_CONFIRMATION,
    /** Daily product-suggestion e-mail (marketing, e-mail only). */
    RECOMMENDATION_DIGEST,
    /** A product the user asked to be alerted about is available again (e-mail + Web Push). */
    STOCK_ALERT
}
