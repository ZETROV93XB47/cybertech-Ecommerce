package com.novatech.cybertech.api.controllers.implementation;

import com.novatech.cybertech.api.controllers.spec.ProductManagementAdminApiSpec;
import com.novatech.cybertech.dto.request.product.ProductCreateRequestDto;
import com.novatech.cybertech.dto.request.product.ProductUpdateRequestDto;
import com.novatech.cybertech.dto.response.product.ProductResponseDto;
import com.novatech.cybertech.services.implementation.ProductManagementServiceImp;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;
import java.util.UUID;

import static com.novatech.cybertech.constants.CyberTechAppConstants.PRODUCT_MANAGEMENT_ADMIN_CONTROLLER_BASE_PATH;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(PRODUCT_MANAGEMENT_ADMIN_CONTROLLER_BASE_PATH)
@Tag(name = "ProductAdminController", description = "API for Product management (Admin)")
@PreAuthorize("hasRole('ADMIN')")
public class ProductManagementAdminController implements ProductManagementAdminApiSpec {

    private final ProductManagementServiceImp productService;


    @Override
    @GetMapping(value = "/get/all", produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<Collection<ProductResponseDto>> getAllProducts() {
        return ResponseEntity.status(HttpStatus.OK).body(productService.getAll());
    }

    @Override
    @PostMapping(value = "/create", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<ProductResponseDto> createProduct(@Valid @RequestBody ProductCreateRequestDto productCreateRequestDto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.create(productCreateRequestDto));
    }

    @Override
    @PatchMapping(value = "/update/{productUuid}", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<ProductResponseDto> updateProduct(@Valid @RequestBody final ProductUpdateRequestDto productUpdateRequestDto) {
        return ResponseEntity.status(HttpStatus.OK).body(productService.update(productUpdateRequestDto));
    }

    @Override
    @DeleteMapping(value = "/delete/{productUuid}", produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> deleteProductByUuid(@PathVariable UUID productUuid) {
        productService.deleteByUUID(productUuid);
        return ResponseEntity.noContent().build();
    }


    @Override
    @PostMapping(value = "/create-with-image", consumes = MULTIPART_FORM_DATA_VALUE, produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<ProductResponseDto> createProductWithImage(
            @Valid @RequestPart("product") final ProductCreateRequestDto productCreateRequestDto,
            @RequestPart("image") final MultipartFile image) {

        return ResponseEntity.status(HttpStatus.CREATED).body(productService.createWithImage(productCreateRequestDto, image));
    }
}