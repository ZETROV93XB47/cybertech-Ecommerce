package com.novatech.cybertech.entities.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EmailTemplateType {

    ORDER_CANCELLATION(NotificationSubject.ORDER_CANCELLATION, "email/order-cancelled"),
    ORDER_PENDING_PAYMENT(NotificationSubject.ORDER_PENDING_PAYMENT, "email/order-pending-payment"),
    ORDER_CONFIRMATION(NotificationSubject.ORDER_CONFIRMATION, "email/order-confirmation"),
    ORDER_UPDATE(NotificationSubject.ORDER_UPDATE, "email/order-update"),
    PAYMENT_CONFIRMATION(NotificationSubject.PAYMENT_CONFIRMATION, "email/payment-confirmation"),
    SHIPPING_CONFIRMATION(NotificationSubject.SHIPPING_CONFIRMATION, "email/shipping-confirmation");

    private final NotificationSubject notificationSubject;
    private final String templatePath;
}