package com.novatech.cybertech.entities.valueObjects;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link Address}. Pure POJO; no Spring/Mockito.
 *
 * Pinned bug (carried over from SA4.5R wave — DO NOT renumber):
 *   - BUG-132: Address is JPA @Embeddable but carries Lombok @Setter — NOT immutable.
 */
class AddressTest {

    @Nested
    @DisplayName("Constructors / builder / accessors")
    class Construction {

        @Test
        void allArgsConstructorAssignsAllFields() {
            final Address address = new Address("12 rue Lafayette", "Paris", "75009", "FR");

            assertThat(address.getStreet()).isEqualTo("12 rue Lafayette");
            assertThat(address.getCity()).isEqualTo("Paris");
            assertThat(address.getZipCode()).isEqualTo("75009");
            assertThat(address.getCountry()).isEqualTo("FR");
        }

        @Test
        void noArgsConstructorYieldsNullFields() {
            // JPA contract.
            final Address address = new Address();

            assertThat(address.getStreet()).isNull();
            assertThat(address.getCity()).isNull();
            assertThat(address.getZipCode()).isNull();
            assertThat(address.getCountry()).isNull();
        }

        @Test
        void builderProducesEquivalentInstance() {
            final Address built = Address.builder()
                    .street("221B Baker Street")
                    .city("London")
                    .zipCode("NW1")
                    .country("UK")
                    .build();

            assertThat(built.getStreet()).isEqualTo("221B Baker Street");
            assertThat(built.getCity()).isEqualTo("London");
            assertThat(built.getZipCode()).isEqualTo("NW1");
            assertThat(built.getCountry()).isEqualTo("UK");
        }

        @Test
        void toStringContainsEveryField() {
            final Address address = new Address("street", "city", "zip", "country");

            assertThat(address.toString())
                    .contains("street")
                    .contains("city")
                    .contains("zip")
                    .contains("country");
        }
    }

    @Nested
    @DisplayName("equals / hashCode")
    class EqualsContract {

        @Test
        void sameValuesAreEqualAndShareHashCode() {
            final Address a = new Address("street", "city", "zip", "FR");
            final Address b = new Address("street", "city", "zip", "FR");

            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        void differentCityIsNotEqual() {
            final Address paris = new Address("street", "Paris", "75000", "FR");
            final Address lyon = new Address("street", "Lyon", "75000", "FR");

            assertThat(paris).isNotEqualTo(lyon);
        }

        @Test
        void notEqualToNullOrDifferentType() {
            final Address a = new Address("street", "city", "zip", "FR");

            assertThat(a).isNotEqualTo(null);
            assertThat(a).isNotEqualTo("not an Address");
        }
    }

    @Nested
    @DisplayName("Mutability gap (BUG-132)")
    class MutabilityGap {

        @Test
        void settersExistDespiteBeingAValueObject_pinsCurrentBehaviour_BUG_132() throws NoSuchMethodException {
            // Address is annotated @Embeddable yet carries Lombok @Setter — these methods exist today.
            // Pin to surface the design gap without requiring a production change.
            final Method setStreet = Address.class.getMethod("setStreet", String.class);
            final Method setCity = Address.class.getMethod("setCity", String.class);
            final Method setZipCode = Address.class.getMethod("setZipCode", String.class);
            final Method setCountry = Address.class.getMethod("setCountry", String.class);

            assertThat(setStreet).isNotNull();
            assertThat(setCity).isNotNull();
            assertThat(setZipCode).isNotNull();
            assertThat(setCountry).isNotNull();
        }

        @Test
        void mutationActuallyChangesEqualsAndHashCode_BUG_132() {
            // The dangerous symptom of BUG-132: a hashed Address whose city is mutated
            // becomes "lost" inside a HashSet — this test reproduces the divergence.
            final Address address = new Address("street", "Paris", "75000", "FR");
            final int hashBefore = address.hashCode();
            final Address twin = new Address("street", "Paris", "75000", "FR");

            assertThat(address).isEqualTo(twin);

            address.setCity("Lyon");

            assertThat(address.hashCode()).isNotEqualTo(hashBefore);
            assertThat(address).isNotEqualTo(twin);
        }

        @Test
        @Disabled("BUG-132 — Address should be immutable: drop @Setter")
        void addressShouldBeImmutable_BUG_132() {
            // Desired contract: there should be no public setter on a value object.
            final long setterCount = java.util.Arrays.stream(Address.class.getDeclaredMethods())
                    .map(Method::getName)
                    .filter(n -> n.startsWith("set"))
                    .count();

            assertThat(setterCount).isZero();
        }
    }
}
