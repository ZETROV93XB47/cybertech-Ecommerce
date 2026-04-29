package com.novatech.cybertech.api.controllers.implementation;

import com.novatech.cybertech.api.controllers.spec.ProductSearchApiSpec;
import com.novatech.cybertech.dto.request.search.ProductSearchRequestDto;
import com.novatech.cybertech.dto.response.product.ProductResponseDto;
import com.novatech.cybertech.services.core.ProductManagementService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static com.novatech.cybertech.constants.CyberTechAppConstants.*;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(version = APP_API_VERSION, value = PRODUCT_CRUD_CONTROLLER_BASE_PATH)
@Tag(name = "ProductSearchController", description = "API for Product Search")
public class ProductSearchController implements ProductSearchApiSpec {

    // FIX(INTERFACE-CONTRACT): inject service interface instead of concrete impl per project convention
    private final ProductManagementService productService;

    @Override
    @GetMapping(value = "/get/{productUuid}", produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<ProductResponseDto> getProductByUuid(@PathVariable("productUuid") UUID productUuid) {
        return ResponseEntity.status(HttpStatus.OK).body(productService.getByUUID(productUuid));
    }

    @Override
    // FIX(DEAD-CODE): removed redundant alias variable; @Override added to enforce ApiSpec contract at compile-time
    @PostMapping(value = "/search", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    public Page<ProductResponseDto> searchProducts(@Valid @RequestBody final ProductSearchRequestDto productSearchRequestDto) {
        log.info("products search :: {}", productSearchRequestDto);
        return productService.searchProducts(productSearchRequestDto);
    }

    @Override
    @GetMapping(value = "/best-sellers", produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<Page<ProductResponseDto>> getBestSellers(
            @PageableDefault(size = DEFAULT_PAGE_SIZE_BEST_SELLERS) final Pageable pageable
    ) {
        return ResponseEntity.ok(productService.getBestSellers(pageable));
    }
}
