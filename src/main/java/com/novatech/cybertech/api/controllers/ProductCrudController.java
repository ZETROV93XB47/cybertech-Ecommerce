package com.novatech.cybertech.api.controllers;

import com.novatech.cybertech.api.controllers.spec.ProductCrudControllerApiSpec;
import com.novatech.cybertech.dto.request.product.ProductCreateRequestDto;
import com.novatech.cybertech.dto.request.product.ProductUpdateRequestDto;
import com.novatech.cybertech.dto.response.product.ProductResponseDto;
import com.novatech.cybertech.entities.ProductEntity;
import com.novatech.cybertech.services.implementation.ProductManagementServiceImp;
import com.novatech.cybertech.utils.DataGenerator;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static com.novatech.cybertech.constants.CyberTechAppConstants.PRODUCT_CRUD_CONTROLLER_BASE_PATH;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequiredArgsConstructor
@RequestMapping(PRODUCT_CRUD_CONTROLLER_BASE_PATH)
@Tag(name = "ProductController", description = "API for Product management")
public class ProductCrudController implements ProductCrudControllerApiSpec {

    private final ProductManagementServiceImp productService;

    @Override
    @GetMapping(value = "/get/{productUuid}", produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<ProductResponseDto> getProductByUuid(@PathVariable("productUuid") UUID productUuid) {
        return ResponseEntity.status(HttpStatus.OK).body(productService.getByUUID(productUuid));
    }

    @Override
    @PostMapping(value = "/create", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<ProductResponseDto> createProduct(@Valid @RequestBody ProductCreateRequestDto productCreateRequestDto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.create(productCreateRequestDto));
    }

    @Override
    @PatchMapping(value = "/update/{productUuid}", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<ProductResponseDto> updateProduct(final ProductUpdateRequestDto productUpdateRequestDto) {
        return ResponseEntity.status(HttpStatus.OK).body(productService.update(productUpdateRequestDto));
    }

    @Override
    @DeleteMapping(value = "/delete/{productUuid}", produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> deleteProductByUuid(UUID productUuid) {
        productService.deleteByUUID(productUuid);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/generate", produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<ProductEntity> generateProduct() {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.temporarySaveProductEntity(DataGenerator.createProductCreateRequestDto()));
    }
}
