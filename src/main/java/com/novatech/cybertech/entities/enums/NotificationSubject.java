package com.novatech.cybertech.entities.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum NotificationSubject {
    ORDER_CANCELLATION("Annulation de commande - Cybertech"),
    ORDER_PENDING_PAYMENT("Action requise : Paiement en attente"),
    ORDER_CONFIRMATION("Confirmation de votre commande"),
    SHIPPING_CONFIRMATION("Votre commande a été expédiée !");

    private final String subject;
}
