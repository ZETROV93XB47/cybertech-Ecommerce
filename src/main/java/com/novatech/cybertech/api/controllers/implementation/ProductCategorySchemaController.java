package com.novatech.cybertech.api.controllers.implementation;

import com.novatech.cybertech.api.controllers.spec.ProductCategorySchemaApiSpec;
import com.novatech.cybertech.dto.response.product.ProductCategorySchemaResponseDto;
import com.novatech.cybertech.services.core.ProductCategorySchemaService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.novatech.cybertech.constants.CyberTechAppConstants.APP_API_VERSION;
import static com.novatech.cybertech.constants.CyberTechAppConstants.PRODUCT_CATEGORY_SCHEMA_CONTROLLER_BASE_PATH;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * Public (non-admin) read access to a single category's JSON Schema — lets a client (e.g. the
 * admin product-creation form) render fields dynamically instead of hardcoding them per category.
 *
 * <p>OpenAPI documentation lives on {@link ProductCategorySchemaApiSpec}.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(version = APP_API_VERSION, value = PRODUCT_CATEGORY_SCHEMA_CONTROLLER_BASE_PATH)
@Tag(name = "ProductCategorySchemaController", description = "Public read access to product category JSON Schemas")
public class ProductCategorySchemaController implements ProductCategorySchemaApiSpec {

    private final ProductCategorySchemaService productCategorySchemaService;

    @Override
    @GetMapping(value = "/{categoryKey}", produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<ProductCategorySchemaResponseDto> getByCategoryKey(@PathVariable final String categoryKey) {
        return ResponseEntity.ok(productCategorySchemaService.getByCategoryKey(categoryKey));
    }
}
