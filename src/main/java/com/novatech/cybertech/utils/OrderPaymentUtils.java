package com.novatech.cybertech.utils;

import com.novatech.cybertech.entities.OrderEntity;
import com.novatech.cybertech.entities.PaymentEntity;
import com.novatech.cybertech.entities.enums.PaymentAttemptStatus;
import com.novatech.cybertech.entities.enums.TransactionType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Single source of truth for "how much has this order actually been paid / refunded", shared by
 * {@code updateOrder} (price difference), {@code cancelOrder} (remaining refundable per payment),
 * the {@code charge.refunded} webhook (partial vs full refund) and the order mapper
 * ({@code refundedAmount}). These used to be copy-pasted with slightly different rules.
 *
 * <p>Accounting rules:
 * <ul>
 *   <li>A PAYMENT counts once it is {@code SUCCESS}. Its row is never downgraded by a refund —
 *       refunds are tracked by their own REFUND rows.</li>
 *   <li>A REFUND counts once Stripe has accepted it: {@code SUCCESS} or {@code PROCESSING}
 *       (Stripe's {@code pending}, typical of some card networks). Stripe itself already deducts a
 *       pending refund from what is still refundable on the charge, so not counting it here made
 *       a later refund request exceed Stripe's ceiling and get rejected.</li>
 *   <li>A payment/refund is rejected when {@code FAILED} or {@code CANCELED}; {@code PROCESSING}
 *       is NOT a rejection — the webhook settles it.</li>
 * </ul>
 */
public final class OrderPaymentUtils {

    private OrderPaymentUtils() {
    }

    /** {@code true} for a REFUND row Stripe has accepted (succeeded or still pending). */
    public static boolean isEffectiveRefund(final PaymentEntity attempt) {
        return attempt.getTransactionType() == TransactionType.REFUND && (attempt.getStatus() == PaymentAttemptStatus.SUCCESS || attempt.getStatus() == PaymentAttemptStatus.PROCESSING);
    }

    /** {@code true} for a PAYMENT row whose money was actually captured. */
    public static boolean isCapturedPayment(final PaymentEntity attempt) {
        return attempt.getTransactionType() == TransactionType.PAYMENT && attempt.getStatus() == PaymentAttemptStatus.SUCCESS;
    }

    /** {@code true} when Stripe definitively refused the payment or refund (card declined, refund failed). */
    public static boolean isRejected(final PaymentEntity attempt) {
        return attempt.getStatus() == PaymentAttemptStatus.FAILED || attempt.getStatus() == PaymentAttemptStatus.CANCELED;
    }

    /** Captured payments minus effective refunds on {@code order}. */
    public static BigDecimal netPaidAmount(final OrderEntity order) {
        return attemptsOf(order).stream()
                .filter(p -> isCapturedPayment(p) || isEffectiveRefund(p))
                .map(p -> p.getTransactionType() == TransactionType.REFUND ? p.getAmount().getAmount().negate() : p.getAmount().getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Sum of effective refunds on {@code order}. */
    public static BigDecimal refundedAmount(final OrderEntity order) {
        return attemptsOf(order).stream()
                .filter(OrderPaymentUtils::isEffectiveRefund)
                .map(p -> p.getAmount().getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * What is still refundable on {@code payment}: its amount minus every effective refund linked
     * to it via {@link PaymentEntity#getOriginalPayment()}.
     */
    public static BigDecimal remainingRefundable(final OrderEntity order, final PaymentEntity payment) {
        final BigDecimal alreadyRefunded = attemptsOf(order).stream()
                .filter(OrderPaymentUtils::isEffectiveRefund)
                .filter(r -> r.getOriginalPayment() != null && Objects.equals(payment.getId(), r.getOriginalPayment().getId()))
                .map(r -> r.getAmount().getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return payment.getAmount().getAmount().subtract(alreadyRefunded);
    }

    // @SuperBuilder ignores OrderEntity's field initializer, so a freshly-built order can carry a
    // null collection rather than an empty one.
    private static List<PaymentEntity> attemptsOf(final OrderEntity order) {
        return Optional.ofNullable(order.getPaymentAttempts()).orElse(List.of());
    }
}
