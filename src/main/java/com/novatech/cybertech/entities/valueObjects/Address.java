package com.novatech.cybertech.entities.valueObjects;

import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.io.Serializable;

/**
 * Immutable shipping / billing {@link Embeddable} value object.
 *
 * <h2>Immutability invariant</h2>
 * <p>
 * {@code Address} carries no public setter — once constructed, its state can never
 * change. Mutating an {@code Address} stored in a {@link java.util.HashSet HashSet}
 * is therefore not possible, side-stepping the classic "lost element" bug that
 * affects mutable {@code @Embeddable} value objects.
 * </p>
 * <p>
 * The {@link NoArgsConstructor no-arg constructor} is mandatory for JPA but is
 * marked {@code PROTECTED} so that application code is steered toward the
 * all-args constructor or the Lombok-generated {@code Builder}.
 * </p>
 * <p>
 * Field-level updates are expressed via the {@code withXxx(...)} wither methods,
 * each of which returns a fresh {@code Address} instance leaving the original
 * untouched.
 * </p>
 */
@Getter
@Builder
@ToString
@Embeddable
@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Address implements Serializable {

    private String street;
    private String city;
    private String zipCode;
    private String country;

    /**
     * Returns a new {@code Address} identical to this one but with the supplied
     * {@code street}.
     *
     * @param newStreet the replacement street value (may be {@code null}).
     * @return a fresh {@code Address}; this instance is unchanged.
     */
    public Address withStreet(final String newStreet) {
        return new Address(newStreet, this.city, this.zipCode, this.country);
    }

    /**
     * Returns a new {@code Address} identical to this one but with the supplied
     * {@code city}.
     *
     * @param newCity the replacement city value (may be {@code null}).
     * @return a fresh {@code Address}; this instance is unchanged.
     */
    public Address withCity(final String newCity) {
        return new Address(this.street, newCity, this.zipCode, this.country);
    }

    /**
     * Returns a new {@code Address} identical to this one but with the supplied
     * {@code zipCode}.
     *
     * @param newZipCode the replacement zip-code value (may be {@code null}).
     * @return a fresh {@code Address}; this instance is unchanged.
     */
    public Address withZipCode(final String newZipCode) {
        return new Address(this.street, this.city, newZipCode, this.country);
    }

    /**
     * Returns a new {@code Address} identical to this one but with the supplied
     * {@code country}.
     *
     * @param newCountry the replacement country value (may be {@code null}).
     * @return a fresh {@code Address}; this instance is unchanged.
     */
    public Address withCountry(final String newCountry) {
        return new Address(this.street, this.city, this.zipCode, newCountry);
    }
}
