package com.novatech.cybertech.entities;


import com.novatech.cybertech.entities.enums.PaymentAttemptStatus;
import com.novatech.cybertech.entities.enums.PaymentType;
import com.novatech.cybertech.entities.enums.TransactionType;
import com.novatech.cybertech.entities.valueObjects.Money;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "paymentTable",
        uniqueConstraints = @UniqueConstraint(name = "uk_payment_idempotency", columnNames = "idempotencyKey")
)
@ToString(callSuper = true, exclude = {"orderEntity", "originalPayment"})
public class PaymentEntity extends BaseEntity<Long> {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "orderId", nullable = false)
    private OrderEntity orderEntity;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "amount", nullable = false)),
            @AttributeOverride(name = "currencyCode", column = @Column(name = "currency", nullable = false, columnDefinition = "VARCHAR(10)"))
    })
    private Money amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "paymentType", nullable = false)
    private PaymentType paymentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "transactionType", nullable = false)
    private TransactionType transactionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentAttemptStatus status;

    @Column(name = "stripePaymentID")
    private String stripePaymentID;//TODO: ID de paiement chez stripe, il faut penser à le renommer en stripePaymentID

    @Column(name = "providerEventId", unique = true)
    private String providerEventId;

    @Column(name = "idempotencyKey", nullable = false, updatable = false, length = 64)
    private String idempotencyKey;

    /**
     * The {@code PAYMENT}-type attempt this row refunds. Only ever set on a {@code REFUND}-type
     * row (null on a {@code PAYMENT} row). Lets a caller compute how much of a given payment is
     * STILL refundable (its {@code amount} minus the sum of {@code SUCCESS} refunds already
     * linked to it) instead of assuming the whole original amount is always refundable — a
     * payment can legitimately be partially refunded more than once (e.g. an {@code updateOrder}
     * partial refund followed later by a full {@code cancelOrder}).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "originalPaymentId")
    private PaymentEntity originalPayment;
}
