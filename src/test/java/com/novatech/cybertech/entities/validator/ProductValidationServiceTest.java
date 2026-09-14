package com.novatech.cybertech.entities.validator;

import com.novatech.cybertech.entities.ProductCategorySchemaEntity;
import com.novatech.cybertech.exceptions.ProductConstraintsViolationException;
import com.novatech.cybertech.exceptions.UnknownProductCategoryException;
import com.novatech.cybertech.repositories.ProductCategorySchemaRepository;
import com.novatech.cybertech.services.implementation.ProductCategorySchemaCacheImp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;

/**
 * Tests for {@link ProductValidationService}. Uses the real {@link ProductCategorySchemaCacheImp}
 * (backed by a mocked repository) and networknt's real JSON Schema engine, plus a real Jackson 3
 * ObjectMapper — the service merely delegates, so mocking any of these would pin the wrong
 * contract. The two schemas mirror what {@code ProductCategorySchemaInitializer} seeds in
 * production (translated from the old {@code ComputerAttributes}/{@code MonitorAttributes}
 * Jakarta records).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductValidationServiceTest {

    private static final String COMPUTER_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "cpu": {"type": "string", "minLength": 1},
                "gpu": {"type": "string", "minLength": 1},
                "ram": {"type": "integer", "minimum": 8},
                "os": {"type": "string", "minLength": 1},
                "connectivity": {"type": "string", "minLength": 1},
                "displayType": {"type": "string", "minLength": 1},
                "memory": {"type": "integer", "minimum": 32}
              },
              "required": ["cpu", "gpu", "ram", "os", "connectivity", "displayType", "memory"]
            }
            """;

    private static final String MONITOR_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "resolution": {"type": "string", "minLength": 1},
                "refreshRate": {"type": "integer", "minimum": 60}
              },
              "required": ["resolution", "refreshRate"]
            }
            """;

    @Mock private ProductCategorySchemaRepository productCategorySchemaRepository;

    private ProductValidationService service;

    @BeforeEach
    void setUp() {
        lenient().when(productCategorySchemaRepository.findByCategoryKey("COMPUTER"))
                .thenReturn(Optional.of(schemaRow("COMPUTER", COMPUTER_SCHEMA)));
        lenient().when(productCategorySchemaRepository.findByCategoryKey("MONITOR"))
                .thenReturn(Optional.of(schemaRow("MONITOR", MONITOR_SCHEMA)));
        lenient().when(productCategorySchemaRepository.findByCategoryKey("TABLET"))
                .thenReturn(Optional.empty());

        final ProductCategorySchemaCacheImp cache = new ProductCategorySchemaCacheImp(productCategorySchemaRepository);
        final ObjectMapper objectMapper = JsonMapper.builder().build();
        service = new ProductValidationService(cache, objectMapper);
    }

    private static ProductCategorySchemaEntity schemaRow(final String categoryKey, final String jsonSchema) {
        return ProductCategorySchemaEntity.builder()
                .categoryKey(categoryKey)
                .label(categoryKey)
                .jsonSchema(jsonSchema)
                .active(true)
                .build();
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
            assertThatCode(() -> service.validateAttributes("COMPUTER", validComputerAttributes()))
                    .doesNotThrowAnyException();
        }

        @ParameterizedTest(name = "Blank required field {0} triggers violation")
        @ValueSource(strings = {"cpu", "gpu", "os", "connectivity", "displayType"})
        void blankRequiredFieldFailsValidation(String fieldName) {
            Map<String, Object> attrs = validComputerAttributes();
            attrs.put(fieldName, "");

            assertThatThrownBy(() -> service.validateAttributes("COMPUTER", attrs))
                    .isInstanceOf(ProductConstraintsViolationException.class);
        }

        @Test
        @DisplayName("minimum(8) ram boundary: 7 fails")
        void ramBelowMinFails() {
            Map<String, Object> attrs = validComputerAttributes();
            attrs.put("ram", 7);

            assertThatThrownBy(() -> service.validateAttributes("COMPUTER", attrs))
                    .isInstanceOf(ProductConstraintsViolationException.class);
        }

        @Test
        @DisplayName("minimum(8) ram boundary: 8 passes")
        void ramAtMinPasses() {
            Map<String, Object> attrs = validComputerAttributes();
            attrs.put("ram", 8);

            assertThatCode(() -> service.validateAttributes("COMPUTER", attrs))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("minimum(32) memory boundary: 31 fails")
        void memoryBelowMinFails() {
            Map<String, Object> attrs = validComputerAttributes();
            attrs.put("memory", 31);

            assertThatThrownBy(() -> service.validateAttributes("COMPUTER", attrs))
                    .isInstanceOf(ProductConstraintsViolationException.class);
        }

        @Test
        @DisplayName("minimum(32) memory boundary: 32 passes")
        void memoryAtMinPasses() {
            Map<String, Object> attrs = validComputerAttributes();
            attrs.put("memory", 32);

            assertThatCode(() -> service.validateAttributes("COMPUTER", attrs))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("missing required field fails")
        void missingRequiredFieldFails() {
            Map<String, Object> attrs = validComputerAttributes();
            attrs.remove("cpu");

            assertThatThrownBy(() -> service.validateAttributes("COMPUTER", attrs))
                    .isInstanceOf(ProductConstraintsViolationException.class);
        }
    }

    @Nested
    @DisplayName("MONITOR category")
    class MonitorCategoryTests {

        @Test
        @DisplayName("Happy path: valid monitor attributes pass without throwing")
        void happyPathValidMonitorAttributes() {
            assertThatCode(() -> service.validateAttributes("MONITOR", validMonitorAttributes()))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Blank required resolution triggers violation")
        void blankResolutionFails() {
            Map<String, Object> attrs = validMonitorAttributes();
            attrs.put("resolution", "");

            assertThatThrownBy(() -> service.validateAttributes("MONITOR", attrs))
                    .isInstanceOf(ProductConstraintsViolationException.class);
        }

        @Test
        @DisplayName("minimum(60) refreshRate boundary: 59 fails")
        void refreshRateBelowMinFails() {
            Map<String, Object> attrs = validMonitorAttributes();
            attrs.put("refreshRate", 59);

            assertThatThrownBy(() -> service.validateAttributes("MONITOR", attrs))
                    .isInstanceOf(ProductConstraintsViolationException.class);
        }

        @Test
        @DisplayName("minimum(60) refreshRate boundary: 60 passes")
        void refreshRateAtMinPasses() {
            Map<String, Object> attrs = validMonitorAttributes();
            attrs.put("refreshRate", 60);

            assertThatCode(() -> service.validateAttributes("MONITOR", attrs))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Unknown / unregistered categories")
    class UnknownCategoryTests {

        @Test
        @DisplayName("A categoryKey with no registered schema throws UnknownProductCategoryException")
        void unregisteredCategoryThrows() {
            assertThatThrownBy(() -> service.validateAttributes("TABLET", validComputerAttributes()))
                    .isInstanceOf(UnknownProductCategoryException.class);
        }

        @Test
        @DisplayName("Null category throws NullPointerException (Caffeine rejects a null key)")
        void nullCategoryThrowsNpe() {
            assertThatThrownBy(() -> service.validateAttributes(null, validComputerAttributes()))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("Violation payload")
    class ViolationPayloadTests {

        @Test
        @DisplayName("Multiple violations are aggregated into a single exception carrying every ValidationMessage")
        void multipleViolationsCollected() {
            Map<String, Object> attrs = validComputerAttributes();
            attrs.put("cpu", "");
            attrs.put("gpu", "");
            attrs.put("ram", 1);

            assertThatThrownBy(() -> service.validateAttributes("COMPUTER", attrs))
                    .isInstanceOf(ProductConstraintsViolationException.class)
                    .satisfies(e -> assertThat(((ProductConstraintsViolationException) e).getViolations())
                            .hasSizeGreaterThanOrEqualTo(3));
        }

        @Test
        @DisplayName("The exception message is derived from the real schema violations, not a generic string")
        void messageReflectsRealViolations() {
            Map<String, Object> attrs = validComputerAttributes();
            attrs.remove("cpu");

            assertThatThrownBy(() -> service.validateAttributes("COMPUTER", attrs))
                    .isInstanceOf(ProductConstraintsViolationException.class)
                    .hasMessageContaining("cpu");
        }
    }
}
