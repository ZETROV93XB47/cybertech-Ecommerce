package com.novatech.cybertech.entities.valueObjects;

import jakarta.persistence.Embeddable;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;

@Getter
@Builder
@ToString
@Embeddable
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class Money implements Serializable {

    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private CurrencyCode currencyCode; // ex: "EUR", "USD"

    public static Money of(final BigDecimal amount) {
        return new Money(amount, CurrencyCode.fromCode("EUR")); // Devise par défaut
    }

    public Money add(final Money other) {
        if (!this.currencyCode.equals(other.currencyCode)) {
            throw new IllegalArgumentException("Cannot add different currencies");
        }
        return new Money(this.amount.add(other.amount), this.currencyCode);
    }
}