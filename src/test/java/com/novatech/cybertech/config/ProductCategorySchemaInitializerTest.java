package com.novatech.cybertech.config;

import com.networknt.schema.InputFormat;
import com.networknt.schema.JsonSchema;
import com.novatech.cybertech.entities.ProductCategorySchemaEntity;
import com.novatech.cybertech.repositories.ProductCategorySchemaRepository;
import com.novatech.cybertech.services.implementation.ProductCategorySchemaCacheImp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductCategorySchemaInitializerTest {

    private static final int SEEDED_CATEGORY_COUNT = 5; // COMPUTER, MONITOR, DESKTOP, SMARTPHONE, SMARTWATCH

    @Mock
    private ProductCategorySchemaRepository productCategorySchemaRepository;

    @InjectMocks
    private ProductCategorySchemaInitializer initializer;

    @Test
    void seedsMissingCategories() {
        when(productCategorySchemaRepository.existsByCategoryKey(anyString())).thenReturn(false);

        initializer.run(null);

        final ArgumentCaptor<ProductCategorySchemaEntity> captor = ArgumentCaptor.forClass(ProductCategorySchemaEntity.class);
        verify(productCategorySchemaRepository, times(SEEDED_CATEGORY_COUNT)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(ProductCategorySchemaEntity::getCategoryKey)
                .containsExactly("COMPUTER", "MONITOR", "DESKTOP", "SMARTPHONE", "SMARTWATCH");
        assertThat(captor.getAllValues()).allMatch(ProductCategorySchemaEntity::isActive);
    }

    @Test
    void skipsExistingCategories() {
        when(productCategorySchemaRepository.existsByCategoryKey(anyString())).thenReturn(true);

        initializer.run(null);

        verify(productCategorySchemaRepository, never()).save(any());
    }

    @Test
    void smartwatchSchemaAcceptsPartialSpecSheetsAndRejectsWrongTypes() {
        final JsonSchema schema = seededSchema("SMARTWATCH");

        assertThat(schema.validate("""
                {"os": "Apple watchOS 26", "caseSize": 42, "caseMaterial": "Aluminium", "batteryLife": 24,
                 "cellular": "Non", "touchscreen": false, "colorDisplay": true, "bluetooth": true, "wifi": false,
                 "compatibleIphone": true, "strapSize": "Unique", "strapMaterial": "Silicone", "strapColor": "Black",
                 "nfc": false, "batteryCapacityMah": 300}
                """, InputFormat.JSON)).isEmpty();
        // A cheap watch sheet often lists none of them: every attribute is optional.
        assertThat(schema.validate("{}", InputFormat.JSON)).isEmpty();
        assertThat(schema.validate("{\"caseSize\": \"42 mm\"}", InputFormat.JSON)).isNotEmpty();
    }

    @Test
    void desktopSchemaRequiresCpuRamAndStorageButNoScreen() {
        final JsonSchema schema = seededSchema("DESKTOP");

        assertThat(schema.validate("""
                {"cpu": "AMD Ryzen 5", "gpu": "NVIDIA GeForce RTX 5060", "ram": 16, "os": "Windows 11",
                 "memory": 1024, "connectivity": "Wi-Fi 6 AX", "formFactor": "Moyen Tour"}
                """, InputFormat.JSON)).isEmpty();
        assertThat(schema.validate("{\"cpu\": \"AMD Ryzen 5\", \"ram\": 16, \"memory\": 512}", InputFormat.JSON)).isEmpty();
        assertThat(schema.validate("{\"cpu\": \"AMD Ryzen 5\", \"ram\": 16}", InputFormat.JSON)).isNotEmpty();
    }

    @Test
    void smartphoneSchemaAcceptsPartialSpecSheetsAndRejectsWrongTypes() {
        final JsonSchema schema = seededSchema("SMARTPHONE");

        assertThat(schema.validate("""
                {"os": "Apple iOS 27", "cpu": "Apple A20 Pro", "storage": 1024, "screenSize": 7.6, "network": "5G",
                 "screenResolution": "1878 x 2670", "ramGB": 12, "batteryCapacityMah": 4950, "weightG": 254,
                 "heightMm": 164.6, "widthMm": 75.2, "thicknessMm": 5.2, "dualSim": false, "simFormat": "eSIM",
                 "nfc": false, "fastCharging": true, "wirelessCharging": false, "waterResistant": true,
                 "foldable": false, "coreCount": 8}
                """, InputFormat.JSON)).isEmpty();
        assertThat(schema.validate("{}", InputFormat.JSON)).isEmpty();
        assertThat(schema.validate("{\"screenSize\": \"7.6 pouces\"}", InputFormat.JSON)).isNotEmpty();
    }

    @Test
    void monitorSchemaKeepsExistingRequirementsAndAcceptsNewOptionalFields() {
        final JsonSchema schema = seededSchema("MONITOR");

        assertThat(schema.validate("""
                {"resolution": "2560 x 1440", "refreshRate": 180, "screenSizeInches": 27,
                 "aspectRatio": "16/9", "panelType": "Dalle IPS", "responseTimeMs": 1,
                 "brightnessNits": 250, "contrastRatio": "1000 /1", "hdr": false, "curved": false,
                 "adaptiveSync": "NVIDIA G-SYNC", "videoInputs": "HDMI", "weightKg": 3.08,
                 "widthMm": 542.04, "heightMm": 407.9, "depthMm": 182.82}
                """, InputFormat.JSON)).isEmpty();
        assertThat(schema.validate("{\"resolution\": \"1920 x 1080\", \"refreshRate\": 60}", InputFormat.JSON)).isEmpty();
        assertThat(schema.validate("""
                {"resolution": "1920 x 1080", "refreshRate": 60, "hdr": "Non"}
                """, InputFormat.JSON)).isNotEmpty();
    }

    @Test
    void computerSchemaAcceptsOptionalDisplayAndHardwareDetails() {
        final JsonSchema schema = seededSchema("COMPUTER");

        assertThat(schema.validate("""
                {"cpu": "Intel Core Ultra 7", "gpu": "Intel Arc", "ram": 16, "os": "Windows 11",
                 "connectivity": "Wi-Fi 6", "displayType": "LED", "memory": 512,
                 "screenSizeInches": 14, "screenResolution": "1920 x 1080", "screenRefreshRateHz": 60,
                 "panelType": "IPS", "weightKg": 1.5, "batteryCapacityWh": 45, "touchscreen": false,
                 "convertible": false, "cpuCores": 6, "storageType": "SSD", "wirelessStandard": "Wi-Fi 6 AX"}
                """, InputFormat.JSON)).isEmpty();
    }

    @Test
    void desktopSchemaAcceptsOptionalStorageAndPhysicalDetails() {
        final JsonSchema schema = seededSchema("DESKTOP");

        assertThat(schema.validate("""
                {"cpu": "AMD Ryzen 5", "ram": 16, "memory": 1024, "storageType": "SSD",
                 "storageInterface": "M.2 - PCI-E 4.0 4x", "powerSupplyW": 500, "videoMemoryGB": 8,
                 "wirelessStandard": "Wi-Fi 6", "ethernetController": "Realtek RTL8111H",
                 "opticalDrive": "None", "widthMm": 168, "heightMm": 353, "depthMm": 265, "weightKg": 8.2}
                """, InputFormat.JSON)).isEmpty();
    }

    /** Runs the initializer with only {@code categoryKey} missing and compiles the schema it seeds. */
    private JsonSchema seededSchema(final String categoryKey) {
        when(productCategorySchemaRepository.existsByCategoryKey(anyString())).thenReturn(true);
        when(productCategorySchemaRepository.existsByCategoryKey(eq(categoryKey))).thenReturn(false);

        initializer.run(null);

        final ArgumentCaptor<ProductCategorySchemaEntity> captor = ArgumentCaptor.forClass(ProductCategorySchemaEntity.class);
        verify(productCategorySchemaRepository).save(captor.capture());
        return new ProductCategorySchemaCacheImp(mock(ProductCategorySchemaRepository.class))
                .compile(captor.getValue().getJsonSchema());
    }
}
