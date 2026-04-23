package com.novatech.cybertech.services.implementation.support;

import com.novatech.cybertech.entities.enums.ShippingType;
import com.novatech.cybertech.services.implementation.DHLShippingProviderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DHLShippingProviderService}.
 *
 * Pins {@code BUG-2512}: {@code deliver} returns a hardcoded marketing string instead of a real
 * tracking number — two calls for the same packageId always return the same response.
 *
 * Pins {@code BUG-2513}: {@code calculateShippingCost} returns hardcoded EXPRESS=25, STANDARD=15
 * with no carrier-specific differentiation.
 */
class DHLShippingProviderServiceTest {

    private final DHLShippingProviderService service = new DHLShippingProviderService();

    @Test
    @DisplayName("BUG-2513: EXPRESS == 25 EUR (hardcoded)")
    void expressCostIs25() {
        assertThat(service.calculateShippingCost(ShippingType.EXPRESS))
                .isEqualByComparingTo(new BigDecimal("25"));
    }

    @Test
    @DisplayName("BUG-2513: STANDARD == 15 EUR (hardcoded)")
    void standardCostIs15() {
        assertThat(service.calculateShippingCost(ShippingType.STANDARD))
                .isEqualByComparingTo(new BigDecimal("15"));
    }

    @Test
    @DisplayName("BUG-2512: deliver(EXPRESS) returns marketing string with the package id, no API call")
    void deliverExpressReturnsMarketingString() {
        String result = service.deliver("PKG-XYZ", ShippingType.EXPRESS);

        assertThat(result)
                .startsWith("DHL ")
                .contains("EXPRESS")
                .endsWith("PKG-XYZ");
    }

    @Test
    @DisplayName("BUG-2512: deliver(STANDARD) for the same packageId yields a deterministic, non-tracking-number string")
    void deliverReturnsHardcodedMessageInsteadOfTrackingNumber() {
        String first = service.deliver("PKG-1", ShippingType.STANDARD);
        String second = service.deliver("PKG-1", ShippingType.STANDARD);

        assertThat(first)
                .as("BUG-2512: identical input -> identical 'tracking' string")
                .isEqualTo(second)
                .isEqualTo("DHL STANDARD delivery for PKG-1");
    }

    @Test
    @DisplayName("the service has no HTTP/API collaborators — proof that the carrier integration is a stub")
    void serviceHasNoCollaboratorFields() {
        assertThat(java.util.Arrays.stream(service.getClass().getDeclaredFields())
                        .filter(f -> !java.lang.reflect.Modifier.isStatic(f.getModifiers()))
                        .toList())
                .as("BUG-2512: no RestTemplate / WebClient / SDK field — the deliver() method cannot call DHL")
                .isEmpty();
    }
}
