package com.novatech.cybertech.factory;

import com.novatech.cybertech.entities.enums.DiscountCalculationType;
import com.novatech.cybertech.strategy.discount.DiscountStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit coverage for {@link DiscountStrategyFactory}. Post-discount-campaign refactor the
 * factory keys on {@link DiscountCalculationType} (algorithm), not the commercial
 * {@code DiscountType} (campaign). Multiple campaigns can therefore share one strategy
 * via the calculation-type indirection.
 */
class DiscountStrategyFactoryTest {

    @Nested
    @DisplayName("happy-path dispatch")
    class HappyPath {

        @ParameterizedTest(name = "{index} - dispatch returns wired strategy for {0}")
        @EnumSource(DiscountCalculationType.class)
        @DisplayName("Every DiscountCalculationType maps to its own registered strategy")
        void dispatchesEachEnumToItsStrategy(final DiscountCalculationType type) {
            final Map<DiscountCalculationType, DiscountStrategy> map = new EnumMap<>(DiscountCalculationType.class);
            for (final DiscountCalculationType t : DiscountCalculationType.values()) {
                final DiscountStrategy stub = Mockito.mock(DiscountStrategy.class, t.name());
                map.put(t, stub);
            }
            final DiscountStrategyFactory factory = new DiscountStrategyFactory(map);

            final DiscountStrategy result = factory.getStrategy(type);

            assertThat(result).isNotNull().isSameAs(map.get(type));
        }

        @Test
        @DisplayName("PERCENTAGE strategy is returned by reference")
        void percentageReturnedByReference() {
            final DiscountStrategy pct = (baseAmount, items, ctx) -> baseAmount.multiply(new BigDecimal("0.4"));
            final Map<DiscountCalculationType, DiscountStrategy> map = new EnumMap<>(DiscountCalculationType.class);
            map.put(DiscountCalculationType.PERCENTAGE, pct);

            final DiscountStrategyFactory factory = new DiscountStrategyFactory(map);

            assertThat(factory.getStrategy(DiscountCalculationType.PERCENTAGE)).isSameAs(pct);
        }
    }

    @Nested
    @DisplayName("misses & edge cases")
    class Misses {

        @Test
        @DisplayName("Unknown calc type (no entry in map) returns null — no throw")
        void unknownEnumReturnsNull() {
            final Map<DiscountCalculationType, DiscountStrategy> map = new EnumMap<>(DiscountCalculationType.class);
            map.put(DiscountCalculationType.PERCENTAGE, Mockito.mock(DiscountStrategy.class));
            final DiscountStrategyFactory factory = new DiscountStrategyFactory(map);

            assertThat(factory.getStrategy(DiscountCalculationType.BUY_ONE_GET_ONE_FREE)).isNull();
        }

        @Test
        @DisplayName("Empty registry returns null for every calc type")
        void emptyRegistryReturnsNull() {
            final DiscountStrategyFactory factory =
                    new DiscountStrategyFactory(new EnumMap<>(DiscountCalculationType.class));

            for (final DiscountCalculationType type : DiscountCalculationType.values()) {
                assertThat(factory.getStrategy(type)).isNull();
            }
        }

        @Test
        @DisplayName("null type with EnumMap-backed registry returns null (EnumMap.get(null) -> null)")
        void nullTypeWithEnumMapReturnsNull() {
            final DiscountStrategyFactory factory =
                    new DiscountStrategyFactory(new EnumMap<>(DiscountCalculationType.class));

            assertThat(factory.getStrategy(null)).isNull();
        }

        @Test
        @DisplayName("null type with HashMap-backed registry returns null (HashMap allows null key, no entry)")
        void nullTypeWithHashMapReturnsNull() {
            final DiscountStrategyFactory factory = new DiscountStrategyFactory(new HashMap<>());

            assertThat(factory.getStrategy(null)).isNull();
        }
    }

    @Nested
    @DisplayName("registration semantics")
    class Registration {

        @Test
        @DisplayName("Duplicate registration: last put() wins")
        void duplicateRegistrationLastWriteWins() {
            final DiscountStrategy first = Mockito.mock(DiscountStrategy.class, "first");
            final DiscountStrategy second = Mockito.mock(DiscountStrategy.class, "second");
            final Map<DiscountCalculationType, DiscountStrategy> map = new EnumMap<>(DiscountCalculationType.class);
            map.put(DiscountCalculationType.PERCENTAGE, first);
            map.put(DiscountCalculationType.PERCENTAGE, second);

            final DiscountStrategyFactory factory = new DiscountStrategyFactory(map);

            assertThat(factory.getStrategy(DiscountCalculationType.PERCENTAGE)).isSameAs(second);
            assertThat(factory.getStrategy(DiscountCalculationType.PERCENTAGE)).isNotSameAs(first);
        }

        @Test
        @DisplayName("Mutating the backing map after construction is visible (factory holds a reference)")
        void mutatingBackingMapIsVisibleByReference() {
            final Map<DiscountCalculationType, DiscountStrategy> map = new EnumMap<>(DiscountCalculationType.class);
            final DiscountStrategyFactory factory = new DiscountStrategyFactory(map);
            assertThat(factory.getStrategy(DiscountCalculationType.PERCENTAGE)).isNull();

            final DiscountStrategy added = Mockito.mock(DiscountStrategy.class);
            map.put(DiscountCalculationType.PERCENTAGE, added);
            assertThat(factory.getStrategy(DiscountCalculationType.PERCENTAGE)).isSameAs(added);
        }
    }
}
