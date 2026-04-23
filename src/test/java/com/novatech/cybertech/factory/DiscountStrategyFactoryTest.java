package com.novatech.cybertech.factory;

import com.novatech.cybertech.entities.enums.DiscountType;
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
 * Unit coverage for {@link DiscountStrategyFactory}. The factory is a thin Map-backed lookup;
 * we verify dispatch for every {@link DiscountType}, missing-key behaviour, duplicate-registration
 * (last-write-wins, BUG-095 shape pin), and the empty-registry default.
 *
 * Per F4: the strategies behind the factory now operate on raw BigDecimal — but the factory is
 * type-agnostic; we only assert which strategy is returned.
 *
 * BUG-094 audit: SA4.1 originally added a null-guard to the production factory that threw
 * {@code DiscountTypeCannotBeNullForStrategy}. That production change was reverted; the
 * factory now simply does {@code map.get(type)}, so {@code null} returns whatever the map
 * does for null. EnumMap.get(null) returns null (does NOT throw). HashMap.get(null) returns null.
 */
class DiscountStrategyFactoryTest {

    @Nested
    @DisplayName("happy-path dispatch")
    class HappyPath {

        @ParameterizedTest(name = "{index} - dispatch returns wired strategy for {0}")
        @EnumSource(DiscountType.class)
        @DisplayName("Every DiscountType maps to its own registered strategy")
        void dispatchesEachEnumToItsStrategy(final DiscountType type) {
            final Map<DiscountType, DiscountStrategy> map = new EnumMap<>(DiscountType.class);
            for (final DiscountType t : DiscountType.values()) {
                final DiscountStrategy stub = Mockito.mock(DiscountStrategy.class, t.name());
                map.put(t, stub);
            }
            final DiscountStrategyFactory factory = new DiscountStrategyFactory(map);

            final DiscountStrategy result = factory.getStrategy(type);

            assertThat(result).isNotNull().isSameAs(map.get(type));
        }

        @Test
        @DisplayName("BLACK_FRIDAY (the only currently-wired strategy in production) is returned by reference")
        void blackFridayReturnedByReference() {
            final DiscountStrategy bf = baseAmount -> baseAmount.multiply(new BigDecimal("0.4"));
            final Map<DiscountType, DiscountStrategy> map = new EnumMap<>(DiscountType.class);
            map.put(DiscountType.BLACK_FRIDAY, bf);

            final DiscountStrategyFactory factory = new DiscountStrategyFactory(map);

            assertThat(factory.getStrategy(DiscountType.BLACK_FRIDAY)).isSameAs(bf);
        }
    }

    @Nested
    @DisplayName("misses & edge cases")
    class Misses {

        @Test
        @DisplayName("Unknown enum (no entry in map) returns null — no throw")
        void unknownEnumReturnsNull() {
            final Map<DiscountType, DiscountStrategy> map = new EnumMap<>(DiscountType.class);
            map.put(DiscountType.BLACK_FRIDAY, Mockito.mock(DiscountStrategy.class));
            final DiscountStrategyFactory factory = new DiscountStrategyFactory(map);

            assertThat(factory.getStrategy(DiscountType.WINTER_SALES)).isNull();
        }

        @Test
        @DisplayName("Empty registry returns null for every enum value")
        void emptyRegistryReturnsNull() {
            final DiscountStrategyFactory factory =
                    new DiscountStrategyFactory(new EnumMap<>(DiscountType.class));

            for (final DiscountType type : DiscountType.values()) {
                assertThat(factory.getStrategy(type)).isNull();
            }
        }

        @Test
        @DisplayName("null type with EnumMap-backed registry returns null (EnumMap.get(null) -> null)")
        void nullTypeWithEnumMapReturnsNull() {
            final DiscountStrategyFactory factory =
                    new DiscountStrategyFactory(new EnumMap<>(DiscountType.class));

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
        @DisplayName("Duplicate registration: last put() wins (BUG-095 shape pin)")
        void duplicateRegistrationLastWriteWins() {
            final DiscountStrategy first = Mockito.mock(DiscountStrategy.class, "first");
            final DiscountStrategy second = Mockito.mock(DiscountStrategy.class, "second");
            final Map<DiscountType, DiscountStrategy> map = new EnumMap<>(DiscountType.class);
            map.put(DiscountType.BLACK_FRIDAY, first);
            map.put(DiscountType.BLACK_FRIDAY, second);

            final DiscountStrategyFactory factory = new DiscountStrategyFactory(map);

            assertThat(factory.getStrategy(DiscountType.BLACK_FRIDAY)).isSameAs(second);
            assertThat(factory.getStrategy(DiscountType.BLACK_FRIDAY)).isNotSameAs(first);
        }

        @Test
        @DisplayName("Mutating the backing map after construction is visible (factory holds a reference)")
        void mutatingBackingMapIsVisibleByReference() {
            final Map<DiscountType, DiscountStrategy> map = new EnumMap<>(DiscountType.class);
            final DiscountStrategyFactory factory = new DiscountStrategyFactory(map);
            assertThat(factory.getStrategy(DiscountType.BLACK_FRIDAY)).isNull();

            final DiscountStrategy added = Mockito.mock(DiscountStrategy.class);
            map.put(DiscountType.BLACK_FRIDAY, added);
            assertThat(factory.getStrategy(DiscountType.BLACK_FRIDAY)).isSameAs(added);
        }
    }
}
