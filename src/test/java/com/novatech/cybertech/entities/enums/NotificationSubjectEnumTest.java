package com.novatech.cybertech.entities.enums;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationSubjectEnumTest {

    @Test
    void hasEightSubjects() {
        assertThat(NotificationSubject.values()).containsExactly(
                NotificationSubject.ORDER_CANCELLATION,
                NotificationSubject.ORDER_PENDING_PAYMENT,
                NotificationSubject.ORDER_CONFIRMATION,
                NotificationSubject.ORDER_UPDATE,
                NotificationSubject.PAYMENT_CONFIRMATION,
                NotificationSubject.SHIPPING_CONFIRMATION,
                NotificationSubject.RECOMMENDATION_DIGEST,
                NotificationSubject.STOCK_ALERT);
    }

    @ParameterizedTest
    @EnumSource(NotificationSubject.class)
    void everySubjectIsNonBlank(final NotificationSubject subject) {
        assertThat(subject.getSubject()).isNotBlank();
    }

    @Test
    void canonicalFrenchCopy() {
        // Pin localised copy — silent edits would change customer-facing email subjects.
        assertThat(NotificationSubject.ORDER_CANCELLATION.getSubject())
                .isEqualTo("Annulation de commande - Cybertech");
        assertThat(NotificationSubject.ORDER_PENDING_PAYMENT.getSubject())
                .isEqualTo("Action requise : Paiement en attente");
        assertThat(NotificationSubject.ORDER_CONFIRMATION.getSubject())
                .isEqualTo("Confirmation de votre commande");
        assertThat(NotificationSubject.PAYMENT_CONFIRMATION.getSubject())
                .isEqualTo("Paiement confirmé - Cybertech");
        assertThat(NotificationSubject.SHIPPING_CONFIRMATION.getSubject())
                .isEqualTo("Votre commande a été expédiée !");
        assertThat(NotificationSubject.RECOMMENDATION_DIGEST.getSubject())
                .isEqualTo("Nos suggestions pour vous - Cybertech");
        assertThat(NotificationSubject.STOCK_ALERT.getSubject())
                .isEqualTo("Un produit que vous attendiez est de nouveau disponible - Cybertech");
    }
}
