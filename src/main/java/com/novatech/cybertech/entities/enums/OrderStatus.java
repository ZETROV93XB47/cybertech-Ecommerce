package com.novatech.cybertech.entities.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OrderStatus {
    CREATED(1, "Créée"),
    AWAITING_PAYMENT(2, "En attente de paiement"),
    PAYMENT_FAILED(3, "Paiement échoué"),
    PAID(4, "Payée"),
    AWAITING_SHIPPING(5, "En préparation"),
    SHIPPED(6, "Expédiée"),
    DELIVERED(7, "Livrée"),
    RETURNED(8, "Retournée"),
    CANCELED(9, "Annulée"),
    REFUNDED(10, "Remboursée");

    private final Integer code;
    /** French label shown to the customer (e-mails); the API keeps exposing the enum name. */
    private final String label;
}