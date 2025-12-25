package com.novatech.cybertech.entities;


import com.novatech.cybertech.entities.enums.PaymentStatus;
import com.novatech.cybertech.entities.enums.PaymentType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Setter
@Getter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true)
@Table(name = "paymentTable")
@EqualsAndHashCode(callSuper = true)
public class PaymentEntity extends BaseEntity {

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "paymentType", nullable = false)
    private PaymentType paymentType; // "VISA", "MASTERCARD", etc.

    @Enumerated(EnumType.STRING)
    @Column(name = "paymentStatus", nullable = false)
    private PaymentStatus paymentStatus; // "SUCCESS", "FAILED", etc.

    @OneToOne(mappedBy = "paymentEntity", fetch = FetchType.EAGER, cascade = CascadeType.ALL)
    private OrderEntity orderEntity;

    @CreationTimestamp
    @Column(name = "paymentDate", nullable = false)
    private LocalDateTime paymentDate;

}
