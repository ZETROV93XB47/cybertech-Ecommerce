package com.novatech.cybertech.api.controllers.implementation;

import com.novatech.cybertech.api.controllers.spec.ProductControllerApiSpec;
import com.novatech.cybertech.dto.request.search.ProductSearchRequestDto;
import com.novatech.cybertech.dto.response.product.ProductResponseDto;
import com.novatech.cybertech.services.implementation.ProductManagementServiceImp;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static com.novatech.cybertech.constants.CyberTechAppConstants.PRODUCT_CRUD_CONTROLLER_BASE_PATH;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequiredArgsConstructor
@RequestMapping(PRODUCT_CRUD_CONTROLLER_BASE_PATH)
@Tag(name = "ProductController", description = "API for Product management")
public class ProductController implements ProductControllerApiSpec {

    private final ProductManagementServiceImp productService;

    @Override
    @GetMapping(value = "/get/{productUuid}", produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<ProductResponseDto> getProductByUuid(@PathVariable("productUuid") UUID productUuid) {
        return ResponseEntity.status(HttpStatus.OK).body(productService.getByUUID(productUuid));
    }

    @PostMapping(value = "/search", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    public List<ProductResponseDto> searchProducts(@Valid @RequestBody final ProductSearchRequestDto productSearchRequestDto) {
        return productService.searchProducts(productSearchRequestDto);
    }
}
