package com.novatech.cybertech.config;

import com.novatech.cybertech.entities.ProductCategorySchemaEntity;
import com.novatech.cybertech.repositories.ProductCategorySchemaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Seeds the product category schemas the demo catalog needs: the two that previously lived as
 * hardcoded Java records ({@code ComputerAttributes}, {@code MonitorAttributes}), so demo data /
 * {@code DataGenerator} keep working out of the box, and DESKTOP, SMARTPHONE and SMARTWATCH for the
 * scraped LDLC catalog. Mirrors {@link DiscountCampaignInitializer}'s
 * find-or-create-on-startup shape.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductCategorySchemaInitializer implements ApplicationRunner {

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

    // A tower has no screen, unlike COMPUTER (which requires displayType): its own category.
    private static final String DESKTOP_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "cpu": {"type": "string", "minLength": 1},
                "gpu": {"type": "string", "minLength": 1},
                "ram": {"type": "integer", "minimum": 1},
                "os": {"type": "string", "minLength": 1},
                "memory": {"type": "integer", "minimum": 1},
                "connectivity": {"type": "string", "minLength": 1},
                "formFactor": {"type": "string", "minLength": 1}
              },
              "required": ["cpu", "ram", "memory"]
            }
            """;

    // Nothing required, as for smartwatches: the importer sends only what a spec sheet has.
    private static final String SMARTPHONE_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "os": {"type": "string", "minLength": 1},
                "cpu": {"type": "string", "minLength": 1},
                "storage": {"type": "integer", "minimum": 1},
                "screenSize": {"type": "number", "exclusiveMinimum": 0},
                "network": {"type": "string", "minLength": 1}
              }
            }
            """;

    // Nothing required: LDLC smartwatch spec sheets are uneven (a cheap watch often lists neither
    // OS nor battery life), so the importer sends only the attributes a sheet actually has.
    private static final String SMARTWATCH_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "os": {"type": "string", "minLength": 1},
                "caseSize": {"type": "integer", "minimum": 1},
                "caseMaterial": {"type": "string", "minLength": 1},
                "batteryLife": {"type": "integer", "minimum": 1},
                "cellular": {"type": "string", "minLength": 1}
              }
            }
            """;

    private final ProductCategorySchemaRepository productCategorySchemaRepository;

    @Override
    public void run(final ApplicationArguments args) {
        seedIfMissing("COMPUTER", "Computers", COMPUTER_SCHEMA);
        seedIfMissing("MONITOR", "Monitors", MONITOR_SCHEMA);
        seedIfMissing("DESKTOP", "Desktop computers", DESKTOP_SCHEMA);
        seedIfMissing("SMARTPHONE", "Smartphones", SMARTPHONE_SCHEMA);
        seedIfMissing("SMARTWATCH", "Smartwatches", SMARTWATCH_SCHEMA);
    }

    private void seedIfMissing(final String categoryKey, final String label, final String jsonSchema) {
        if (productCategorySchemaRepository.existsByCategoryKey(categoryKey)) {
            return;
        }
        productCategorySchemaRepository.save(ProductCategorySchemaEntity.builder()
                .categoryKey(categoryKey)
                .label(label)
                .jsonSchema(jsonSchema)
                .active(true)
                .build());
        log.info("Seeded missing product category schema for {}", categoryKey);
    }
}
