package com.novatech.cybertech.config;

import com.novatech.cybertech.entities.ProductCategorySchemaEntity;
import com.novatech.cybertech.repositories.ProductCategorySchemaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Seeds the two product category schemas that previously lived as hardcoded Java records
 * ({@code ComputerAttributes}, {@code MonitorAttributes}) so demo data / {@code DataGenerator}
 * keep working out of the box. Mirrors {@link DiscountCampaignInitializer}'s
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

    private final ProductCategorySchemaRepository productCategorySchemaRepository;

    @Override
    public void run(final ApplicationArguments args) {
        seedIfMissing("COMPUTER", "Computers", COMPUTER_SCHEMA);
        seedIfMissing("MONITOR", "Monitors", MONITOR_SCHEMA);
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
