package com.novatech.cybertech.entities.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke tests for marker enums (constants only, no business methods).
 * One assertion each: {@code values().length} pinned + {@code valueOf(name)} round-trip
 * sample. Adding a new constant or removing one will trip exactly one assertion below.
 */
class MarkerEnumsSmokeEnumTest {

    @Test
    void bankCardTypeHasFiveConstants() {
        assertThat(BankCardType.values()).containsExactly(
                BankCardType.VISA,
                BankCardType.MASTERCARD,
                BankCardType.AMERICAN_EXPRESS,
                BankCardType.DISCOVER,
                BankCardType.OTHER);
        assertThat(BankCardType.valueOf("VISA")).isSameAs(BankCardType.VISA);
    }

    @Test
    void eventCategoryHasThreeConstants() {
        assertThat(EventCategory.values()).containsExactly(
                EventCategory.EXPLICIT_EVENT,
                EventCategory.IMPLICIT_EVENT,
                EventCategory.NEGATIVE_EVENT);
    }

    @Test
    void notificationStatusHasFourConstants() {
        // Phase 1 added PENDING_RETRY between SENT and the terminal FAILED state.
        // See NotificationStatus javadoc for the lifecycle diagram.
        assertThat(NotificationStatus.values()).containsExactly(
                NotificationStatus.PENDING,
                NotificationStatus.SENT,
                NotificationStatus.PENDING_RETRY,
                NotificationStatus.FAILED);
    }

    @Test
    void notificationTypeHasFourConstants() {
        assertThat(NotificationType.values()).containsExactly(
                NotificationType.ORDER_CONFIRMATION,
                NotificationType.ORDER_UPDATE,
                NotificationType.SHIPPING_CONFIRMATION,
                NotificationType.PAYMENT_CONFIRMATION);
    }

    @Test
    void orderActionsHasThreeConstants() {
        assertThat(OrderActions.values()).containsExactly(
                OrderActions.PLACE,
                OrderActions.UPDATE,
                OrderActions.CANCEL);
    }

    @Test
    void paymentServiceProviderHasThreeConstants() {
        assertThat(PaymentServiceProvider.values()).containsExactly(
                PaymentServiceProvider.STRIPE,
                PaymentServiceProvider.AYDEN,
                PaymentServiceProvider.PAYMENT_DOT_COM);
    }

    @Test
    void reservationStatusHasFourConstants() {
        assertThat(ReservationStatus.values()).containsExactly(
                ReservationStatus.ACTIVE,
                ReservationStatus.COMMITTED,
                ReservationStatus.RELEASED,
                ReservationStatus.EXPIRED);
    }

    @Test
    void shippingProviderHasTwoConstants() {
        assertThat(ShippingProvider.values()).containsExactly(
                ShippingProvider.DHL,
                ShippingProvider.FEDEX);
    }

    @Test
    void shippingTypeHasTwoConstants() {
        assertThat(ShippingType.values()).containsExactly(
                ShippingType.STANDARD,
                ShippingType.EXPRESS);
    }

    @Test
    void transactionTypeHasTwoConstants() {
        assertThat(TransactionType.values()).containsExactly(
                TransactionType.PAYMENT,
                TransactionType.REFUND);
    }
}
