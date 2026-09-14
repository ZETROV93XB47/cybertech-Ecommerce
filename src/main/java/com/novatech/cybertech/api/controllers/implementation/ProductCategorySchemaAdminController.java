package com.novatech.cybertech.api.controllers.implementation;

import com.novatech.cybertech.api.controllers.spec.ProductCategorySchemaAdminApiSpec;
import com.novatech.cybertech.dto.request.product.ProductCategorySchemaCreateRequestDto;
import com.novatech.cybertech.dto.request.product.ProductCategorySchemaUpdateRequestDto;
import com.novatech.cybertech.dto.response.product.ProductCategorySchemaResponseDto;
import com.novatech.cybertech.services.core.ProductCategorySchemaService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.novatech.cybertech.constants.CyberTechAppConstants.APP_API_VERSION;
import static com.novatech.cybertech.constants.CyberTechAppConstants.PRODUCT_CATEGORY_SCHEMA_ADMIN_CONTROLLER_BASE_PATH;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * Admin-only back office for the product-category-schema registry — the mechanism that lets a
 * new product category be added via an API call instead of a Java code change + redeploy.
 *
 * <p>OpenAPI documentation lives on {@link ProductCategorySchemaAdminApiSpec}.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(version = APP_API_VERSION, value = PRODUCT_CATEGORY_SCHEMA_ADMIN_CONTROLLER_BASE_PATH)
@Tag(name = "ProductCategorySchemaAdminController", description = "Admin management of product category JSON Schemas")
@PreAuthorize("hasRole('ADMIN')")
public class ProductCategorySchemaAdminController implements ProductCategorySchemaAdminApiSpec {

    private final ProductCategorySchemaService productCategorySchemaService;

    @Override
    @GetMapping(value = "/get/all", produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<List<ProductCategorySchemaResponseDto>> getAll() {
        return ResponseEntity.ok(productCategorySchemaService.getAll());
    }

    @Override
    @GetMapping(value = "/get/{categoryKey}", produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<ProductCategorySchemaResponseDto> getByCategoryKey(@PathVariable final String categoryKey) {
        return ResponseEntity.ok(productCategorySchemaService.getByCategoryKey(categoryKey));
    }

    @Override
    @PostMapping(value = "/create", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<ProductCategorySchemaResponseDto> create(@Valid @RequestBody final ProductCategorySchemaCreateRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productCategorySchemaService.create(request));
    }

    @Override
    @PatchMapping(value = "/update/{categoryKey}", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<ProductCategorySchemaResponseDto> update(@PathVariable final String categoryKey,
                                                                    @Valid @RequestBody final ProductCategorySchemaUpdateRequestDto request) {
        return ResponseEntity.ok(productCategorySchemaService.update(categoryKey, request));
    }

    @Override
    @DeleteMapping(value = "/delete/{categoryKey}")
    public ResponseEntity<Void> delete(@PathVariable final String categoryKey) {
        productCategorySchemaService.deleteByCategoryKey(categoryKey);
        return ResponseEntity.noContent().build();
    }
}
