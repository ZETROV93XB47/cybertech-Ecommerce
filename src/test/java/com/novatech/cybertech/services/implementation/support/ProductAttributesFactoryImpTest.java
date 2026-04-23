package com.novatech.cybertech.services.implementation.support;

import com.novatech.cybertech.entities.enums.Category;
import com.novatech.cybertech.mappers.document.ComputerProductAttributes;
import com.novatech.cybertech.mappers.document.SpecificProductAttributes;
import com.novatech.cybertech.services.implementation.ProductAttributesFactoryImp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ProductAttributesFactoryImp}.
 *
 * Pins {@code BUG-2514}: unchecked attribute casts throw a raw {@link ClassCastException} with no
 * field-level context if a caller passes mis-typed values.
 *
 * Pins {@code BUG-2515}: {@code Category.MACBOOK} is missing from the switch — {@code create(MACBOOK, …)}
 * silently returns {@code null} instead of raising a domain exception.
 *
 * Pins {@code BUG-2516}: COMPUTER, MONITOR, SMARTPHONE, KEYBOARD all share the same
 * {@link ComputerProductAttributes} shape — no per-category modelling.
 */
class ProductAttributesFactoryImpTest {

    private final ProductAttributesFactoryImp factory = new ProductAttributesFactoryImp();

    private Map<String, Object> validComputerRaw() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("ram", 16);
        raw.put("memory", 512);
        raw.put("cpu", "AMD Ryzen 7");
        raw.put("gpu", "NVIDIA RTX 3070");
        raw.put("os", "Windows 11");
        raw.put("displayType", "OLED");
        raw.put("connectivity", "WiFi 6");
        return raw;
    }

    @Test
    @DisplayName("happy: COMPUTER builds a ComputerProductAttributes keyed by 'COMPUTER'")
    void computerCategoryBuildsComputerAttributes() {
        Map<String, SpecificProductAttributes> result = factory.create(Category.COMPUTER, validComputerRaw());

        assertThat(result).containsOnlyKeys("COMPUTER");
        ComputerProductAttributes attrs = (ComputerProductAttributes) result.get("COMPUTER");
        assertThat(attrs.getRam()).isEqualTo(16);
        assertThat(attrs.getMemory()).isEqualTo(512);
        assertThat(attrs.getCpu()).isEqualTo("AMD Ryzen 7");
        assertThat(attrs.getGpu()).isEqualTo("NVIDIA RTX 3070");
        assertThat(attrs.getOs()).isEqualTo("Windows 11");
        assertThat(attrs.getDisplayType()).isEqualTo("OLED");
        assertThat(attrs.getConnectivity()).isEqualTo("WiFi 6");
    }

    @Test
    @DisplayName("BUG-2516: MONITOR also returns a ComputerProductAttributes — no monitor-specific model")
    void monitorCategoryAlsoBuildsComputerAttributes() {
        Map<String, SpecificProductAttributes> result = factory.create(Category.MONITOR, validComputerRaw());

        assertThat(result).containsOnlyKeys("MONITOR");
        assertThat(result.get("MONITOR")).isInstanceOf(ComputerProductAttributes.class);
    }

    @Test
    @DisplayName("BUG-2516: SMARTPHONE and KEYBOARD also share the ComputerProductAttributes builder")
    void smartphoneAndKeyboardUseSameBuilder() {
        Map<String, SpecificProductAttributes> phone = factory.create(Category.SMARTPHONE, validComputerRaw());
        Map<String, SpecificProductAttributes> kbd = factory.create(Category.KEYBOARD, validComputerRaw());

        assertThat(phone.get("SMARTPHONE")).isInstanceOf(ComputerProductAttributes.class);
        assertThat(kbd.get("KEYBOARD")).isInstanceOf(ComputerProductAttributes.class);
    }

    @Test
    @DisplayName("BUG-2515: MACBOOK is on the enum but missing from the switch — returns null silently")
    void unknownCategoryReturnsNull() {
        Map<String, SpecificProductAttributes> result = factory.create(Category.MACBOOK, validComputerRaw());

        assertThat(result)
                .as("BUG-2515: silent null instead of NoStrategyFoundForProcessingTheRequest")
                .isNull();
    }

    @Test
    @DisplayName("BUG-2514: a String passed where Integer 'ram' is expected throws raw ClassCastException")
    void wrongTypedAttributeThrowsClassCastException() {
        Map<String, Object> bad = validComputerRaw();
        bad.put("ram", "sixteen-gigs"); // String instead of Integer

        assertThatThrownBy(() -> factory.create(Category.COMPUTER, bad))
                .as("BUG-2514: caller gets no info about which attribute was malformed")
                .isInstanceOf(ClassCastException.class);
    }

    @Test
    @DisplayName("missing keys are allowed — values become null on the resulting attributes object")
    void missingKeysProduceNullFields() {
        Map<String, Object> sparse = new HashMap<>();
        sparse.put("cpu", "Intel Core i5");
        // Other keys absent.

        Map<String, SpecificProductAttributes> result = factory.create(Category.COMPUTER, sparse);

        ComputerProductAttributes attrs = (ComputerProductAttributes) result.get("COMPUTER");
        assertThat(attrs.getCpu()).isEqualTo("Intel Core i5");
        assertThat(attrs.getRam()).isNull();
        assertThat(attrs.getMemory()).isNull();
        assertThat(attrs.getGpu()).isNull();
        assertThat(attrs.getOs()).isNull();
        assertThat(attrs.getDisplayType()).isNull();
        assertThat(attrs.getConnectivity()).isNull();
    }
}
