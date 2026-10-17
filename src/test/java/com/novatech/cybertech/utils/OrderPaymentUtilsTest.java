package com.novatech.cybertech.utils;

import com.novatech.cybertech.entities.OrderEntity;
import com.novatech.cybertech.entities.PaymentEntity;
import com.novatech.cybertech.entities.enums.PaymentAttemptStatus;
import com.novatech.cybertech.entities.enums.TransactionType;
import com.novatech.cybertech.entities.valueObjects.CurrencyCode;
import com.novatech.cybertech.entities.valueObjects.Money;
import com.novatech.cybertech.fixtures.builders.OrderEntityBuilder;
import com.novatech.cybertech.fixtures.builders.PaymentEntityBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderPaymentUtilsTest {

    private static PaymentEntity attempt(final TransactionType type, final PaymentAttemptStatus status, final String amount) {
        return PaymentEntityBuilder.aValidPaymentBuilder()
                .transactionType(type)
                .status(status)
                .amount(new Money(new BigDecimal(amount), CurrencyCode.EUR))
                .build();
    }

    private static OrderEntity orderWith(final PaymentEntity... attempts) {
        return OrderEntityBuilder.aValidOrderBuilder().paymentAttempts(new ArrayList<>(List.of(attempts))).build();
    }

    @Test
    @DisplayName("netPaid = captured payments - accepted refunds (SUCCESS and pending PROCESSING); failed rows ignored")
    void netPaid_countsCapturedPaymentsAndAcceptedRefunds() {
        final OrderEntity order = orderWith(
                attempt(TransactionType.PAYMENT, PaymentAttemptStatus.SUCCESS, "100.00"),
                attempt(TransactionType.PAYMENT, PaymentAttemptStatus.FAILED, "50.00"),
                attempt(TransactionType.REFUND, PaymentAttemptStatus.SUCCESS, "20.00"),
                attempt(TransactionType.REFUND, PaymentAttemptStatus.PROCESSING, "10.00"),
                attempt(TransactionType.REFUND, PaymentAttemptStatus.FAILED, "5.00"));

        assertThat(OrderPaymentUtils.netPaidAmount(order)).isEqualByComparingTo("70.00");
        assertThat(OrderPaymentUtils.refundedAmount(order)).isEqualByComparingTo("30.00");
    }

    @Test
    @DisplayName("null payment collection (fresh @SuperBuilder order) -> zero, no NPE")
    void nullCollection_isZero() {
        final OrderEntity order = OrderEntityBuilder.aValidOrderBuilder().paymentAttempts(null).build();

        assertThat(OrderPaymentUtils.netPaidAmount(order)).isEqualByComparingTo("0");
        assertThat(OrderPaymentUtils.refundedAmount(order)).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("remainingRefundable only subtracts accepted refunds linked to THAT payment")
    void remainingRefundable_perPayment() {
        final PaymentEntity payment = PaymentEntityBuilder.aValidPaymentBuilder().id(1L)
                .transactionType(TransactionType.PAYMENT).status(PaymentAttemptStatus.SUCCESS)
                .amount(new Money(new BigDecimal("100.00"), CurrencyCode.EUR)).build();
        final PaymentEntity other = PaymentEntityBuilder.aValidPaymentBuilder().id(2L)
                .transactionType(TransactionType.PAYMENT).status(PaymentAttemptStatus.SUCCESS)
                .amount(new Money(new BigDecimal("50.00"), CurrencyCode.EUR)).build();
        final PaymentEntity linkedRefund = attempt(TransactionType.REFUND, PaymentAttemptStatus.PROCESSING, "30.00");
        linkedRefund.setOriginalPayment(payment);
        final PaymentEntity otherRefund = attempt(TransactionType.REFUND, PaymentAttemptStatus.SUCCESS, "50.00");
        otherRefund.setOriginalPayment(other);
        final PaymentEntity failedRefund = attempt(TransactionType.REFUND, PaymentAttemptStatus.FAILED, "70.00");
        failedRefund.setOriginalPayment(payment);
        final OrderEntity order = orderWith(payment, other, linkedRefund, otherRefund, failedRefund);

        assertThat(OrderPaymentUtils.remainingRefundable(order, payment)).isEqualByComparingTo("70.00");
        assertThat(OrderPaymentUtils.remainingRefundable(order, other)).isEqualByComparingTo("0");
    }

    @ParameterizedTest
    @EnumSource(value = PaymentAttemptStatus.class, names = {"FAILED", "CANCELED"})
    @DisplayName("FAILED and CANCELED are rejections")
    void rejectedStatuses(final PaymentAttemptStatus status) {
        assertThat(OrderPaymentUtils.isRejected(attempt(TransactionType.REFUND, status, "1.00"))).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = PaymentAttemptStatus.class, names = {"SUCCESS", "PROCESSING", "CREATED"})
    @DisplayName("SUCCESS, PROCESSING (Stripe pending) and CREATED are not rejections")
    void nonRejectedStatuses(final PaymentAttemptStatus status) {
        assertThat(OrderPaymentUtils.isRejected(attempt(TransactionType.PAYMENT, status, "1.00"))).isFalse();
    }
}
