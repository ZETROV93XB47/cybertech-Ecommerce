package com.novatech.cybertech.entities.valueObjects;

import jakarta.persistence.Embeddable;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Immutable monetary value object pairing a {@link BigDecimal} amount with a
 * {@link CurrencyCode}.
 *
 * <h2>Equality contract</h2>
 * <p>
 * {@link #equals(Object)} is <b>currency-aware</b> and <b>scale-insensitive</b>:
 * two {@code Money} instances are equal iff they share the same {@link CurrencyCode}
 * <i>and</i> their amounts compare equal via {@link BigDecimal#compareTo(BigDecimal)}
 * (so {@code Money(10.00, EUR).equals(Money(10, EUR))} returns {@code true}).
 * </p>
 * <p>
 * {@link #hashCode()} is consistent with {@link #equals(Object)}: any two equal
 * instances yield the same hash, which is achieved by stripping trailing zeros
 * from the amount before hashing and collapsing all numeric zeros to a single
 * canonical form.
 * </p>
 *
 * <h2>Immutability</h2>
 * <p>
 * No public setter exists. Arithmetic helpers ({@link #add(Money)},
 * {@link #subtract(Money)}, {@link #multiply(BigDecimal)}) all return a fresh
 * {@code Money} instance, leaving operands untouched. The
 * {@link NoArgsConstructor no-arg constructor} is required for JPA but should not
 * be used by application code.
 * </p>
 */
@Getter
@Builder
@ToString
@Embeddable
@NoArgsConstructor
@AllArgsConstructor
public class Money implements Serializable {

    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private CurrencyCode currencyCode; // ex: "EUR", "USD"

    /**
     * Convenience factory yielding a {@code Money} expressed in the default
     * currency ({@link CurrencyCode#EUR}).
     *
     * @param amount the monetary amount; may be {@code null}.
     * @return a new {@code Money(amount, EUR)} instance.
     */
    public static Money of(final BigDecimal amount) {
        return new Money(amount, CurrencyCode.fromCode("EUR")); // Devise par défaut
    }

    /**
     * Returns a new {@code Money} representing {@code this + other}.
     *
     * @param other the right-hand operand; must share this instance's {@link CurrencyCode}.
     * @return a new {@code Money} carrying the summed amount in this currency.
     * @throws IllegalArgumentException if {@code other.currencyCode} differs from this instance's currency.
     */
    public Money add(final Money other) {
        requireSameCurrency(other);
        return new Money(this.amount.add(other.amount), this.currencyCode);
    }

    /**
     * Returns a new {@code Money} representing {@code this - other}.
     *
     * @param other the right-hand operand; must share this instance's {@link CurrencyCode}.
     * @return a new {@code Money} carrying the subtracted amount in this currency.
     * @throws IllegalArgumentException if {@code other.currencyCode} differs from this instance's currency.
     */
    public Money subtract(final Money other) {
        requireSameCurrency(other);
        return new Money(this.amount.subtract(other.amount), this.currencyCode);
    }

    /**
     * Returns a new {@code Money} representing {@code this * factor}, preserving
     * the current currency. Useful for tax / discount / quantity multipliers.
     *
     * @param factor the multiplier; must not be {@code null}.
     * @return a new {@code Money} carrying the scaled amount in this currency.
     */
    public Money multiply(final BigDecimal factor) {
        return new Money(this.amount.multiply(factor), this.currencyCode);
    }

    /**
     * Equality contract: currency-aware, scale-insensitive (uses
     * {@link BigDecimal#compareTo(BigDecimal)} on the amount). See class-level
     * Javadoc for the full discussion.
     */
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Money other)) {
            return false;
        }
        if (this.currencyCode != other.currencyCode) {
            return false;
        }
        if (this.amount == null || other.amount == null) {
            return this.amount == other.amount;
        }
        return this.amount.compareTo(other.amount) == 0;
    }

    /**
     * Hash code consistent with {@link #equals(Object)}: any two {@code Money}
     * instances that compare equal share the same hash. The amount is normalised
     * by stripping trailing zeros so that {@code 10.00} and {@code 10} hash
     * identically; the special case where {@link BigDecimal#stripTrailingZeros()}
     * preserves scale on zero is handled explicitly.
     */
    @Override
    public int hashCode() {
        final int amountHash;
        if (amount == null) {
            amountHash = 0;
        } else if (amount.signum() == 0) {
            // BigDecimal("0.00").stripTrailingZeros() differs from BigDecimal.ZERO.stripTrailingZeros();
            // collapse all numeric zeros to the same hash to match compareTo-based equality.
            amountHash = 0;
        } else {
            final BigDecimal normalised = amount.stripTrailingZeros();
            amountHash = Objects.hash(normalised.unscaledValue(), normalised.scale());
        }
        return Objects.hash(currencyCode, amountHash);
    }

    private void requireSameCurrency(final Money other) {
        if (!this.currencyCode.equals(other.currencyCode)) {
            throw new IllegalArgumentException("Cannot add different currencies");
        }
    }
}
