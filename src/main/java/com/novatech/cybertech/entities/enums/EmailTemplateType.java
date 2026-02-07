package com.novatech.cybertech.entities.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EmailTemplateType {

    ORDER_CANCELLATION("Annulation de commande - Cybertech", "email/order-cancelled"),
    ORDER_PENDING_PAYMENT("Action requise : Paiement en attente", "email/order-pending-payment"),
    ORDER_CONFIRMATION("Confirmation de votre commande", "email/order-confirmation"),
    SHIPPING_CONFIRMATION("Votre commande a été expédiée !", "email/shipping-confirmation");

    private final String subject;
    private final String templatePath;
}