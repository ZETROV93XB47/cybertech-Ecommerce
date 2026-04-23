package com.novatech.cybertech.factory;

import com.novatech.cybertech.entities.enums.ShippingProvider;
import com.novatech.cybertech.services.core.ShippingProviderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mockito;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit coverage for {@link ShippingProviderStrategyFactory} — same Map-backed shape as
 * the notification factories. SA4.1R note: unknown key returns null (no throw).
 *
 * Only DHL / FEDEX exist on {@link ShippingProvider} today; if a future enum value is added,
 * the EnumSource test will catch any coverage gap automatically.
 */
class ShippingProviderStrategyFactoryTest {

    @Nested
    @DisplayName("happy-path dispatch")
    class HappyPath {

        @ParameterizedTest(name = "{index} - resolves {0}")
        @EnumSource(ShippingProvider.class)
        @DisplayName("Each ShippingProvider resolves to the registered ShippingProviderService")
        void dispatchesEachProviderToItsService(final ShippingProvider provider) {
            final Map<ShippingProvider, ShippingProviderService> map = new EnumMap<>(ShippingProvider.class);
            for (final ShippingProvider p : ShippingProvider.values()) {
                map.put(p, Mockito.mock(ShippingProviderService.class, p.name()));
            }
            final ShippingProviderStrategyFactory factory = new ShippingProviderStrategyFactory(map);

            assertThat(factory.getStrategy(provider)).isSameAs(map.get(provider));
        }

        @Test
        @DisplayName("DHL and FEDEX dispatch to distinct services (no cross-talk)")
        void dhlAndFedexAreDistinct() {
            final ShippingProviderService dhl = Mockito.mock(ShippingProviderService.class, "dhl");
            final ShippingProviderService fedex = Mockito.mock(ShippingProviderService.class, "fedex");
            final Map<ShippingProvider, ShippingProviderService> map = new EnumMap<>(ShippingProvider.class);
            map.put(ShippingProvider.DHL, dhl);
            map.put(ShippingProvider.FEDEX, fedex);
            final ShippingProviderStrategyFactory factory = new ShippingProviderStrategyFactory(map);

            assertThat(factory.getStrategy(ShippingProvider.DHL)).isSameAs(dhl);
            assertThat(factory.getStrategy(ShippingProvider.FEDEX)).isSameAs(fedex);
        }
    }

    @Nested
    @DisplayName("misses & null behaviour")
    class Misses {

        @Test
        @DisplayName("Missing entry returns null (no throw)")
        void missingEntryReturnsNull() {
            final Map<ShippingProvider, ShippingProviderService> map = new EnumMap<>(ShippingProvider.class);
            map.put(ShippingProvider.DHL, Mockito.mock(ShippingProviderService.class));
            final ShippingProviderStrategyFactory factory = new ShippingProviderStrategyFactory(map);

            assertThat(factory.getStrategy(ShippingProvider.FEDEX)).isNull();
        }

        @Test
        @DisplayName("Empty registry returns null for every provider")
        void emptyRegistryReturnsNull() {
            final ShippingProviderStrategyFactory factory =
                    new ShippingProviderStrategyFactory(new EnumMap<>(ShippingProvider.class));

            for (final ShippingProvider p : ShippingProvider.values()) {
                assertThat(factory.getStrategy(p)).isNull();
            }
        }

        @Test
        @DisplayName("EnumMap-backed factory: get(null) returns null (no NPE)")
        void enumMapNullKeyReturnsNull() {
            final ShippingProviderStrategyFactory factory =
                    new ShippingProviderStrategyFactory(new EnumMap<>(ShippingProvider.class));

            assertThat(factory.getStrategy(null)).isNull();
        }

        @Test
        @DisplayName("HashMap-backed factory: get(null) returns null")
        void hashMapNullKeyReturnsNull() {
            final ShippingProviderStrategyFactory factory =
                    new ShippingProviderStrategyFactory(new HashMap<>());

            assertThat(factory.getStrategy(null)).isNull();
        }
    }

    @Nested
    @DisplayName("registration semantics")
    class Registration {

        @Test
        @DisplayName("Duplicate registration last-write-wins (BUG-095 shape pin)")
        void duplicateRegistrationLastWriteWins() {
            final ShippingProviderService first = Mockito.mock(ShippingProviderService.class, "first");
            final ShippingProviderService second = Mockito.mock(ShippingProviderService.class, "second");
            final Map<ShippingProvider, ShippingProviderService> map = new EnumMap<>(ShippingProvider.class);
            map.put(ShippingProvider.DHL, first);
            map.put(ShippingProvider.DHL, second);
            final ShippingProviderStrategyFactory factory = new ShippingProviderStrategyFactory(map);

            assertThat(factory.getStrategy(ShippingProvider.DHL)).isSameAs(second);
        }
    }
}
