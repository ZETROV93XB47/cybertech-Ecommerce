package com.novatech.cybertech.entities.valueObjects;

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
    @DisplayName("Immutability (BUG-132 fixed)")
    class Immutability {

        @Test
        void noPublicSettersExposed_pinsFix_BUG_132() {
            // Address no longer carries Lombok @Setter — confirm zero set* methods exist.
            final long setterCount = java.util.Arrays.stream(Address.class.getDeclaredMethods())
                    .map(Method::getName)
                    .filter(n -> n.startsWith("set"))
                    .count();

            assertThat(setterCount).isZero();
        }

        @Test
        void addressShouldBeImmutable_BUG_132() {
            // Desired contract: there should be no public setter on a value object.
            final long setterCount = java.util.Arrays.stream(Address.class.getDeclaredMethods())
                    .map(Method::getName)
                    .filter(n -> n.startsWith("set"))
                    .count();

            assertThat(setterCount).isZero();
        }

        @Test
        void witherReturnsFreshInstanceLeavingOriginalUntouched_BUG_132() {
            final Address original = new Address("street", "Paris", "75000", "FR");

            final Address mutated = original.withCity("Lyon");

            assertThat(mutated).isNotSameAs(original);
            assertThat(original.getCity()).isEqualTo("Paris");
            assertThat(mutated.getCity()).isEqualTo("Lyon");
            assertThat(mutated.getStreet()).isEqualTo(original.getStreet());
            assertThat(mutated.getZipCode()).isEqualTo(original.getZipCode());
            assertThat(mutated.getCountry()).isEqualTo(original.getCountry());
        }

        @Test
        void witherSurfaceCoversAllFields_BUG_132() throws NoSuchMethodException {
            assertThat(Address.class.getMethod("withStreet", String.class)).isNotNull();
            assertThat(Address.class.getMethod("withCity", String.class)).isNotNull();
            assertThat(Address.class.getMethod("withZipCode", String.class)).isNotNull();
            assertThat(Address.class.getMethod("withCountry", String.class)).isNotNull();
        }
    }
}
