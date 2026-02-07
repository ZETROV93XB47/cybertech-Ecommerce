package com.novatech.cybertech.entities;


import com.novatech.cybertech.entities.enums.PaymentAttemptStatus;
import com.novatech.cybertech.entities.enums.PaymentType;
import com.novatech.cybertech.entities.valueObjects.Money;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Setter
@Getter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "paymentAttemptTable",
        uniqueConstraints = @UniqueConstraint(name = "uk_payment_attempt_idem", columnNames = "idempotencyKey")
)
@ToString(callSuper = true, exclude = {"orderEntity"})
public class PaymentAttemptEntity extends BaseEntity<Long> {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "orderId", nullable = false)
    private OrderEntity orderEntity;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "amount", nullable = false)),
            @AttributeOverride(name = "currencyCode", column = @Column(name = "currency", nullable = false))
    })
    private Money amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "paymentType", nullable = false)
    private PaymentType paymentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentAttemptStatus status;

    @Column(name = "providerRef")
    private String providerRef;

    @Column(name = "idempotencyKey", nullable = false, updatable = false, length = 64)
    private String idempotencyKey;
}
