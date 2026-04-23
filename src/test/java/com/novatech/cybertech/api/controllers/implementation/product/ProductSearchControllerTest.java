package com.novatech.cybertech.api.controllers.implementation.product;

import com.novatech.cybertech.api.controllers.TestSecurityConfig;
import com.novatech.cybertech.api.controllers.implementation.ProductSearchController;
import com.novatech.cybertech.api.error.model.ErrorResponseDto;
import com.novatech.cybertech.dto.request.search.ProductSearchRequestDto;
import com.novatech.cybertech.dto.response.product.ProductResponseDto;
import com.novatech.cybertech.entities.enums.Category;
import com.novatech.cybertech.exceptions.ProductNotFoundException;
import com.novatech.cybertech.fixtures.dto.ProductDtoFixtures;
import com.novatech.cybertech.services.implementation.ProductManagementServiceImp;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static com.novatech.cybertech.api.error.enumpackage.ErrorCodeType.FUNCTIONAL;
import static com.novatech.cybertech.api.error.enumpackage.ErrorCodeType.TECHNICAL;
import static com.novatech.cybertech.fixtures.support.JwtTestUtils.jwtAdmin;
import static com.novatech.cybertech.fixtures.support.JwtTestUtils.jwtAnonymous;
import static com.novatech.cybertech.utils.TestUtils.asJsonString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.json.JsonCompareMode.STRICT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Slf4j
@Import({TestSecurityConfig.class})
@WebMvcTest(value = ProductSearchController.class)
class ProductSearchControllerTest {

    private static final String GET_PRODUCT_BY_UUID_ENDPOINT = "/api/v1/services/product/get/{productUuid}";
    private static final String SEARCH_PRODUCTS_ENDPOINT = "/api/v1/services/product/search";
    private static final String GET_BEST_SELLERS_ENDPOINT = "/api/v1/services/product/best-sellers";

    private static final String ADMIN_KEYCLOAK_ID = "admin-keycloak-id";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ProductManagementServiceImp productService;

    // ----------------------------------------------------------------------
    // GET /get/{productUuid}
    // ----------------------------------------------------------------------

    @Test
    void shouldGetProductByUuidAsAnonymous() throws Exception {
        // BUG-032 (closed by W0 TestSecurityConfig PUBLIC_URLS update): /api/v1/services/product/**
        // is now whitelisted, so anonymous reads succeed.
        UUID productUuid = UUID.randomUUID();
        ProductResponseDto productResponseDto = ProductDtoFixtures.aSampleProductResponseBuilder()
                .uuid(productUuid.toString())
                .build();

        when(productService.getByUUID(productUuid)).thenReturn(productResponseDto);

        mockMvc.perform(get(GET_PRODUCT_BY_UUID_ENDPOINT, productUuid)
                        .with(jwtAnonymous())
                        .accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(productResponseDto), STRICT));
    }

    @Test
    void shouldGetProductByUuidAsAuthenticatedUserSuccessfully() throws Exception {
        UUID productUuid = UUID.randomUUID();
        ProductResponseDto productResponseDto = ProductDtoFixtures.aSampleProductResponseBuilder()
                .uuid(productUuid.toString())
                .build();

        when(productService.getByUUID(productUuid)).thenReturn(productResponseDto);

        mockMvc.perform(get(GET_PRODUCT_BY_UUID_ENDPOINT, productUuid)
                        .with(jwtAdmin(ADMIN_KEYCLOAK_ID))
                        .accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(productResponseDto), STRICT));
    }

    @Test
    void shouldFailGetProductByUuidCauseProductNotFound() throws Exception {
        UUID productUuid = UUID.randomUUID();
        ErrorResponseDto errorResponseDto = ErrorResponseDto.builder()
                .message("No product with the UUID : " + productUuid + " found")
                .httpStatusCode(404)
                .errorCodeType(FUNCTIONAL)
                .build();

        when(productService.getByUUID(productUuid))
                .thenThrow(new ProductNotFoundException("No product with the UUID : " + productUuid + " found"));

        mockMvc.perform(get(GET_PRODUCT_BY_UUID_ENDPOINT, productUuid)
                        .with(jwtAnonymous())
                        .accept(APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(errorResponseDto), STRICT));
    }

    @Test
    void shouldRejectGetProductByUuidWhenPathUuidIsMalformed() throws Exception {
        // BUG-029 (closed by F2): MethodArgumentTypeMismatchException → 400 TECHNICAL.
        ErrorResponseDto errorResponseDto = ErrorResponseDto.builder()
                .message("Invalid value for parameter 'productUuid'")
                .httpStatusCode(400)
                .errorCodeType(TECHNICAL)
                .build();

        mockMvc.perform(get(GET_PRODUCT_BY_UUID_ENDPOINT, "not-a-uuid")
                        .with(jwtAnonymous())
                        .accept(APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(errorResponseDto), STRICT));
    }

    // ----------------------------------------------------------------------
    // POST /search
    // ----------------------------------------------------------------------

    @Test
    void shouldSearchProductsAsAnonymousSuccessfully() throws Exception {
        // BUG-032 (closed by W0): /api/v1/services/product/** is whitelisted; anonymous POST allowed.
        ProductSearchRequestDto searchRequestDto = ProductSearchRequestDto.builder()
                .keyword("laptop")
                .category(Category.COMPUTER)
                .priceMin(150.0)
                .priceMax(50000.0)
                .page(0)
                .size(10)
                .build();

        ProductResponseDto productResponseDto = ProductDtoFixtures.aSampleProductResponse();
        Page<ProductResponseDto> page = new PageImpl<>(List.of(productResponseDto));

        when(productService.searchProducts(any(ProductSearchRequestDto.class))).thenReturn(page);

        mockMvc.perform(post(SEARCH_PRODUCTS_ENDPOINT)
                        .with(jwtAnonymous())
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON)
                        .content(asJsonString(searchRequestDto)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(jsonPath("$.content[0].uuid").value(productResponseDto.getUuid()))
                .andExpect(jsonPath("$.content[0].name").value(productResponseDto.getName()))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void shouldFailSearchProductsCauseDtoBadRequest() throws Exception {
        // category is @NotNull → triggers MethodArgumentNotValidException → 400.
        ProductSearchRequestDto invalidDto = ProductSearchRequestDto.builder()
                .keyword("laptop")
                .priceMin(200.0)
                .build();

        ErrorResponseDto errorResponseDto = ErrorResponseDto.builder()
                .message("Invalid Request or Request Poorly Constructed")
                .httpStatusCode(400)
                .errorCodeType(TECHNICAL)
                .build();

        mockMvc.perform(post(SEARCH_PRODUCTS_ENDPOINT)
                        .with(jwtAnonymous())
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON)
                        .content(asJsonString(invalidDto)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(errorResponseDto), STRICT));
    }

    @Test
    void shouldFailSearchProductsCausePriceMinBelowThreshold() throws Exception {
        // priceMin must be >= 100 per ProductSearchRequestDto @Min(100).
        ProductSearchRequestDto invalidDto = ProductSearchRequestDto.builder()
                .keyword("laptop")
                .category(Category.COMPUTER)
                .priceMin(50.0) // below 100 threshold
                .build();

        ErrorResponseDto errorResponseDto = ErrorResponseDto.builder()
                .message("Invalid Request or Request Poorly Constructed")
                .httpStatusCode(400)
                .errorCodeType(TECHNICAL)
                .build();

        mockMvc.perform(post(SEARCH_PRODUCTS_ENDPOINT)
                        .with(jwtAnonymous())
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON)
                        .content(asJsonString(invalidDto)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(errorResponseDto), STRICT));
    }

    @Test
    void shouldSearchProductsAsAuthenticatedUserSuccessfully() throws Exception {
        ProductSearchRequestDto searchRequestDto = ProductSearchRequestDto.builder()
                .keyword("laptop")
                .category(Category.COMPUTER)
                .priceMin(150.0)
                .priceMax(50000.0)
                .page(0)
                .size(10)
                .build();

        Page<ProductResponseDto> page = new PageImpl<>(List.of(ProductDtoFixtures.aSampleProductResponse()));
        when(productService.searchProducts(any(ProductSearchRequestDto.class))).thenReturn(page);

        mockMvc.perform(post(SEARCH_PRODUCTS_ENDPOINT)
                        .with(jwtAdmin(ADMIN_KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON)
                        .content(asJsonString(searchRequestDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    // ----------------------------------------------------------------------
    // GET /best-sellers
    // ----------------------------------------------------------------------

    @Test
    void shouldGetBestSellersAsAnonymousSuccessfully() throws Exception {
        // BUG-032 (closed by W0): /api/v1/services/product/** whitelisted; anonymous GET allowed.
        ProductResponseDto productResponseDto = ProductDtoFixtures.aSampleProductResponse();
        Page<ProductResponseDto> page = new PageImpl<>(List.of(productResponseDto));

        when(productService.getBestSellers(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get(GET_BEST_SELLERS_ENDPOINT)
                        .with(jwtAnonymous())
                        .accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(jsonPath("$.content[0].uuid").value(productResponseDto.getUuid()))
                .andExpect(jsonPath("$.content[0].name").value(productResponseDto.getName()))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void shouldGetBestSellersAsAuthenticatedUserSuccessfully() throws Exception {
        ProductResponseDto productResponseDto = ProductDtoFixtures.aSampleProductResponse();
        Page<ProductResponseDto> page = new PageImpl<>(List.of(productResponseDto));

        when(productService.getBestSellers(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get(GET_BEST_SELLERS_ENDPOINT)
                        .with(jwtAdmin(ADMIN_KEYCLOAK_ID))
                        .accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].uuid").value(productResponseDto.getUuid()))
                .andExpect(jsonPath("$.totalElements").value(1));
    }
}
