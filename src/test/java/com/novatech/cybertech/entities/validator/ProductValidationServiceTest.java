package com.novatech.cybertech.entities.validator;

import com.novatech.cybertech.entities.enums.Category;
import com.novatech.cybertech.exceptions.ProductConstraintsViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link ProductValidationService}. Uses the real Jakarta Bean Validation provider
 * (Hibernate Validator) and a real Jackson 3 ObjectMapper — the service merely delegates so
 * mocking either would pin the wrong contract.
 */
class ProductValidationServiceTest {

    private static ValidatorFactory factory;
    private static Validator beanValidator;
    private static ObjectMapper objectMapper;
    private static ProductValidationService service;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        beanValidator = factory.getValidator();
        objectMapper = JsonMapper.builder().build();
        service = new ProductValidationService(beanValidator, objectMapper);
    }

    @AfterAll
    static void tearDown() {
        if (factory != null) {
            factory.close();
        }
    }

    private static Map<String, Object> validComputerAttributes() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("cpu", "Intel i7");
        attrs.put("gpu", "RTX 4070");
        attrs.put("ram", 16);
        attrs.put("os", "Linux");
        attrs.put("connectivity", "Wi-Fi 6");
        attrs.put("displayType", "OLED");
        attrs.put("memory", 512);
        return attrs;
    }

    private static Map<String, Object> validMonitorAttributes() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("resolution", "3840x2160");
        attrs.put("refreshRate", 144);
        return attrs;
    }

    @Nested
    @DisplayName("COMPUTER category")
    class ComputerCategoryTests {

        @Test
        @DisplayName("Happy path: valid computer attributes pass without throwing")
        void happyPathValidComputerAttributes() {
            assertThatCode(() -> service.validateAttributes(Category.COMPUTER, validComputerAttributes()))
                    .doesNotThrowAnyException();
        }

        @ParameterizedTest(name = "Blank @NotBlank field {0} triggers violation")
        @ValueSource(strings = {"cpu", "gpu", "os", "connectivity", "displayType"})
        void blankNotBlankFieldFailsValidation(String fieldName) {
            Map<String, Object> attrs = validComputerAttributes();
            attrs.put(fieldName, "");

            assertThatThrownBy(() -> service.validateAttributes(Category.COMPUTER, attrs))
                    .isInstanceOf(ProductConstraintsViolationException.class)
                    .hasMessageContaining("constraints");
        }

        @Test
        @DisplayName("@Min(8) ram boundary: 7 fails")
        void ramBelowMinFails() {
            Map<String, Object> attrs = validComputerAttributes();
            attrs.put("ram", 7);

            assertThatThrownBy(() -> service.validateAttributes(Category.COMPUTER, attrs))
                    .isInstanceOf(ProductConstraintsViolationException.class);
        }

        @Test
        @DisplayName("@Min(8) ram boundary: 8 passes")
        void ramAtMinPasses() {
            Map<String, Object> attrs = validComputerAttributes();
            attrs.put("ram", 8);

            assertThatCode(() -> service.validateAttributes(Category.COMPUTER, attrs))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("@Min(32) memory boundary: 31 fails")
        void memoryBelowMinFails() {
            Map<String, Object> attrs = validComputerAttributes();
            attrs.put("memory", 31);

            assertThatThrownBy(() -> service.validateAttributes(Category.COMPUTER, attrs))
                    .isInstanceOf(ProductConstraintsViolationException.class);
        }

        @Test
        @DisplayName("@Min(32) memory boundary: 32 passes")
        void memoryAtMinPasses() {
            Map<String, Object> attrs = validComputerAttributes();
            attrs.put("memory", 32);

            assertThatCode(() -> service.validateAttributes(Category.COMPUTER, attrs))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("MONITOR category")
    class MonitorCategoryTests {

        @Test
        @DisplayName("Happy path: valid monitor attributes pass without throwing")
        void happyPathValidMonitorAttributes() {
            assertThatCode(() -> service.validateAttributes(Category.MONITOR, validMonitorAttributes()))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Blank @NotBlank resolution triggers violation")
        void blankResolutionFails() {
            Map<String, Object> attrs = validMonitorAttributes();
            attrs.put("resolution", "");

            assertThatThrownBy(() -> service.validateAttributes(Category.MONITOR, attrs))
                    .isInstanceOf(ProductConstraintsViolationException.class);
        }

        @Test
        @DisplayName("@Min(60) refreshRate boundary: 59 fails")
        void refreshRateBelowMinFails() {
            Map<String, Object> attrs = validMonitorAttributes();
            attrs.put("refreshRate", 59);

            assertThatThrownBy(() -> service.validateAttributes(Category.MONITOR, attrs))
                    .isInstanceOf(ProductConstraintsViolationException.class);
        }

        @Test
        @DisplayName("@Min(60) refreshRate boundary: 60 passes")
        void refreshRateAtMinPasses() {
            Map<String, Object> attrs = validMonitorAttributes();
            attrs.put("refreshRate", 60);

            assertThatCode(() -> service.validateAttributes(Category.MONITOR, attrs))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Switch defaults / unsupported categories")
    class UnsupportedCategoryTests {

        @ParameterizedTest(name = "Category {0} is not handled and falls into default → IAE")
        @EnumSource(value = Category.class, names = {"KEYBOARD", "SMARTPHONE", "MACBOOK"})
        void unsupportedCategoryThrowsIae(Category category) {
            assertThatThrownBy(() -> service.validateAttributes(category, validComputerAttributes()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Catégorie inconnue");
        }

        @Test
        @DisplayName("Null category: switch throws NullPointerException")
        void nullCategoryThrowsNpe() {
            assertThatThrownBy(() -> service.validateAttributes(null, validComputerAttributes()))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("Violation payload")
    class ViolationPayloadTests {

        @Test
        @DisplayName("Multiple violations are aggregated into a single exception")
        void multipleViolationsCollected() {
            Map<String, Object> attrs = validComputerAttributes();
            attrs.put("cpu", "");
            attrs.put("gpu", "");
            attrs.put("ram", 1);

            assertThatThrownBy(() -> service.validateAttributes(Category.COMPUTER, attrs))
                    .isInstanceOf(ProductConstraintsViolationException.class);
        }

        @Test
        @DisplayName("Sanity: real Jakarta validator was wired and reports zero violations on happy path")
        void realValidatorIsWired() {
            // independent sanity check that the test setup uses a real validator
            assertThat(beanValidator).isNotNull();
            assertThatCode(() -> service.validateAttributes(Category.COMPUTER, validComputerAttributes()))
                    .doesNotThrowAnyException();
        }
    }
}
