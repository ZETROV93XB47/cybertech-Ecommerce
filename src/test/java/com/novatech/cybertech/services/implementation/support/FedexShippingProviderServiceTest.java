package com.novatech.cybertech.services.implementation.support;

import com.novatech.cybertech.entities.enums.ShippingType;
import com.novatech.cybertech.services.implementation.DHLShippingProviderService;
import com.novatech.cybertech.services.implementation.FedexShippingProviderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link FedexShippingProviderService}.
 *
 * Pins the fact that {@code deliver} returns a hardcoded string instead of a real tracking number.
 *
 * Pins the fact that FEDEX and DHL produce identical prices — the provider abstraction adds no
 * carrier-specific pricing.
 */
class FedexShippingProviderServiceTest {

    private final FedexShippingProviderService fedex = new FedexShippingProviderService();
    private final DHLShippingProviderService dhl = new DHLShippingProviderService();

    @Test
    @DisplayName("EXPRESS == 25 EUR (hardcoded)")
    void expressCostIs25() {
        assertThat(fedex.calculateShippingCost(ShippingType.EXPRESS))
                .isEqualByComparingTo(new BigDecimal("25"));
    }

    @Test
    @DisplayName("STANDARD == 15 EUR (hardcoded)")
    void standardCostIs15() {
        assertThat(fedex.calculateShippingCost(ShippingType.STANDARD))
                .isEqualByComparingTo(new BigDecimal("15"));
    }

    @Test
    @DisplayName("FedEx and DHL return identical prices for both shipping types")
    void fedexAndDhlReturnIdenticalPrices() {
        assertThat(fedex.calculateShippingCost(ShippingType.STANDARD))
                .as("identical STANDARD prices")
                .isEqualByComparingTo(dhl.calculateShippingCost(ShippingType.STANDARD));
        assertThat(fedex.calculateShippingCost(ShippingType.EXPRESS))
                .as("identical EXPRESS prices")
                .isEqualByComparingTo(dhl.calculateShippingCost(ShippingType.EXPRESS));
    }

    @Test
    @DisplayName("deliver(EXPRESS) returns marketing string with the package id, no API call")
    void deliverExpressReturnsMarketingString() {
        String result = fedex.deliver("PKG-A", ShippingType.EXPRESS);

        assertThat(result)
                .startsWith("FEDEX ")
                .contains("EXPRESS")
                .endsWith("PKG-A");
    }

    @Test
    @DisplayName("deliver(STANDARD) returns deterministic hardcoded string, not a tracking number")
    void deliverReturnsHardcodedStringInsteadOfTrackingNumber() {
        String first = fedex.deliver("PKG-2", ShippingType.STANDARD);
        String second = fedex.deliver("PKG-2", ShippingType.STANDARD);

        assertThat(first).isEqualTo(second).isEqualTo("FEDEX STANDARD delivery for PKG-2");
    }

    @Test
    @DisplayName("the service has no HTTP/API collaborators — proof that the carrier integration is a stub")
    void serviceHasNoCollaboratorFields() {
        assertThat(java.util.Arrays.stream(fedex.getClass().getDeclaredFields())
                        .filter(f -> !java.lang.reflect.Modifier.isStatic(f.getModifiers()))
                        .toList())
                .as("no RestTemplate / WebClient / SDK field — the deliver() method cannot call FedEx")
                .isEmpty();
    }
}
