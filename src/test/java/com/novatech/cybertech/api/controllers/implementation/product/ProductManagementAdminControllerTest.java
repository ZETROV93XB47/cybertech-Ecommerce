package com.novatech.cybertech.api.controllers.implementation.product;

import com.novatech.cybertech.api.controllers.TestSecurityConfig;
import com.novatech.cybertech.api.controllers.implementation.ProductManagementAdminController;
import com.novatech.cybertech.api.error.model.ErrorResponseDto;
import com.novatech.cybertech.dto.request.product.ProductCreateRequestDto;
import com.novatech.cybertech.dto.request.product.ProductUpdateRequestDto;
import com.novatech.cybertech.dto.response.product.ProductResponseDto;
import com.novatech.cybertech.exceptions.ProductConstraintsViolationException;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static com.novatech.cybertech.api.error.enumpackage.ErrorCodeType.FUNCTIONAL;
import static com.novatech.cybertech.api.error.enumpackage.ErrorCodeType.TECHNICAL;
import static com.novatech.cybertech.fixtures.support.JwtTestUtils.jwtAdmin;
import static com.novatech.cybertech.fixtures.support.JwtTestUtils.jwtAnonymous;
import static com.novatech.cybertech.fixtures.support.JwtTestUtils.jwtUser;
import static com.novatech.cybertech.utils.TestUtils.asJsonString;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.json.JsonCompareMode.STRICT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Slf4j
@Import({TestSecurityConfig.class})
@WebMvcTest(value = ProductManagementAdminController.class)
class ProductManagementAdminControllerTest {

    private static final String GET_ALL_PRODUCTS_ENDPOINT = "/api/v1/services/admin/management/product/get/all";
    private static final String CREATE_PRODUCT_ENDPOINT = "/api/v1/services/admin/management/product/create";
    private static final String CREATE_PRODUCT_WITH_IMAGE_ENDPOINT = "/api/v1/services/admin/management/product/create-with-image";
    private static final String UPDATE_PRODUCT_ENDPOINT = "/api/v1/services/admin/management/product/update/{productUuid}";
    private static final String DELETE_PRODUCT_ENDPOINT = "/api/v1/services/admin/management/product/delete/{productUuid}";

    private static final String ADMIN_KEYCLOAK_ID = "admin-keycloak-id";
    private static final String USER_KEYCLOAK_ID = "user-keycloak-id";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ProductManagementServiceImp productService;

    // ----------------------------------------------------------------------
    // GET /get/all
    // ----------------------------------------------------------------------

    @Test
    void shouldGetAllProductsAsAdminSuccessfully() throws Exception {
        ProductResponseDto product = ProductDtoFixtures.aSampleProductResponse();
        Page<ProductResponseDto> page = new PageImpl<>(List.of(product));
        when(productService.getAll(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get(GET_ALL_PRODUCTS_ENDPOINT)
                        .with(jwtAdmin(ADMIN_KEYCLOAK_ID))
                        .accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(jsonPath("$.content[0].uuid").value(product.getUuid()))
                .andExpect(jsonPath("$.content[0].name").value(product.getName()))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.size").value(1));
    }

    @Test
    void shouldRejectGetAllProductsWhenAnonymous() throws Exception {
        mockMvc.perform(get(GET_ALL_PRODUCTS_ENDPOINT)
                        .with(jwtAnonymous())
                        .accept(APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldForbidGetAllProductsForNonAdminUser() throws Exception {
        // BUG-031 (closed by F1/F2): non-admin → 403 (was 500 before the AccessDenied handler landed).
        ErrorResponseDto errorResponseDto = ErrorResponseDto.builder()
                .message("Access denied")
                .httpStatusCode(403)
                .errorCodeType(FUNCTIONAL)
                .build();

        mockMvc.perform(get(GET_ALL_PRODUCTS_ENDPOINT)
                        .with(jwtUser(USER_KEYCLOAK_ID))
                        .accept(APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(content().json(asJsonString(errorResponseDto), STRICT));
    }

    // ----------------------------------------------------------------------
    // POST /create
    // ----------------------------------------------------------------------

    @Test
    void shouldCreateProductAsAdminSuccessfully() throws Exception {
        ProductCreateRequestDto createRequestDto = ProductDtoFixtures.aValidCreateRequest();
        ProductResponseDto productResponseDto = ProductDtoFixtures.aSampleProductResponse();

        when(productService.create(any(ProductCreateRequestDto.class))).thenReturn(productResponseDto);

        mockMvc.perform(post(CREATE_PRODUCT_ENDPOINT)
                        .with(jwtAdmin(ADMIN_KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON)
                        .content(asJsonString(createRequestDto)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(productResponseDto), STRICT));
    }

    @Test
    void shouldFailCreateProductCauseDtoBadRequest() throws Exception {
        ProductCreateRequestDto invalidDto = new ProductCreateRequestDto();

        mockMvc.perform(post(CREATE_PRODUCT_ENDPOINT)
                        .with(jwtAdmin(ADMIN_KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON)
                        .content(asJsonString(invalidDto)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(jsonPath("$.message", startsWith("Validation failed:")))
                .andExpect(jsonPath("$.httpStatusCode").value(400))
                .andExpect(jsonPath("$.errorCodeType").value("TECHNICAL"));
    }

    @Test
    void shouldFailCreateProductCauseConstraintsViolation() throws Exception {
        ProductCreateRequestDto createRequestDto = ProductDtoFixtures.aValidCreateRequest();
        ErrorResponseDto errorResponseDto = ErrorResponseDto.builder()
                .message("The product you're trying to save's attributes aren't matching the constraints criterias, please check your product category and attributes fields to make sure that they're matching together")
                .httpStatusCode(400)
                .errorCodeType(TECHNICAL)
                .build();

        when(productService.create(any(ProductCreateRequestDto.class)))
                .thenThrow(new ProductConstraintsViolationException("attributes invalid"));

        mockMvc.perform(post(CREATE_PRODUCT_ENDPOINT)
                        .with(jwtAdmin(ADMIN_KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON)
                        .content(asJsonString(createRequestDto)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(errorResponseDto), STRICT));
    }

    @Test
    void shouldRejectCreateProductWhenAnonymous() throws Exception {
        ProductCreateRequestDto createRequestDto = ProductDtoFixtures.aValidCreateRequest();

        mockMvc.perform(post(CREATE_PRODUCT_ENDPOINT)
                        .with(jwtAnonymous())
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON)
                        .content(asJsonString(createRequestDto)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldForbidCreateProductForNonAdminUser() throws Exception {
        // BUG-031 (closed by F1/F2): non-admin → 403, was 500 pre-fix.
        ProductCreateRequestDto createRequestDto = ProductDtoFixtures.aValidCreateRequest();
        ErrorResponseDto errorResponseDto = ErrorResponseDto.builder()
                .message("Access denied")
                .httpStatusCode(403)
                .errorCodeType(FUNCTIONAL)
                .build();

        mockMvc.perform(post(CREATE_PRODUCT_ENDPOINT)
                        .with(jwtUser(USER_KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON)
                        .content(asJsonString(createRequestDto)))
                .andExpect(status().isForbidden())
                .andExpect(content().json(asJsonString(errorResponseDto), STRICT));
    }

    // ----------------------------------------------------------------------
    // PATCH /update/{productUuid}
    // ----------------------------------------------------------------------

    @Test
    void shouldUpdateProductAsAdminSuccessfully() throws Exception {
        UUID productUuid = UUID.randomUUID();
        ProductUpdateRequestDto updateRequestDto = ProductDtoFixtures.aValidUpdateRequest();
        updateRequestDto.setProductUuid(productUuid);

        ProductResponseDto productResponseDto = ProductDtoFixtures.aSampleProductResponseBuilder()
                .uuid(productUuid.toString())
                .build();

        when(productService.update(any(ProductUpdateRequestDto.class))).thenReturn(productResponseDto);

        mockMvc.perform(patch(UPDATE_PRODUCT_ENDPOINT, productUuid)
                        .with(jwtAdmin(ADMIN_KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON)
                        .content(asJsonString(updateRequestDto)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(productResponseDto), STRICT));
    }

    @Test
    void shouldRejectUpdateProductWhenBodyUuidIsNull() throws Exception {
        // BUG-380 [TEST-CONTRACT TENSION]: ProductManagementAdminController#updateProduct (post-F2)
        // contains a "fill body UUID from path when null" branch — but ProductUpdateRequestDto
        // declares @NotNull on productUuid, so @Valid trips first and that branch is unreachable
        // through the HTTP boundary. Pin: null body UUID → 400 TECHNICAL (validation), not the
        // 200 the controller code would otherwise produce.
        UUID productUuid = UUID.randomUUID();
        ProductUpdateRequestDto updateRequestDto = ProductDtoFixtures.aValidUpdateRequest();
        updateRequestDto.setProductUuid(null);

        mockMvc.perform(patch(UPDATE_PRODUCT_ENDPOINT, productUuid)
                        .with(jwtAdmin(ADMIN_KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON)
                        .content(asJsonString(updateRequestDto)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(jsonPath("$.message", startsWith("Validation failed:")))
                .andExpect(jsonPath("$.httpStatusCode").value(400))
                .andExpect(jsonPath("$.errorCodeType").value("TECHNICAL"));
    }

    @Test
    void shouldRejectUpdateWhenPathAndBodyUuidMismatch() throws Exception {
        // BUG-033 (closed by F2): controller now throws IllegalArgumentException when path
        // and body UUIDs differ. ErrorManagementController maps that to 400 TECHNICAL.
        UUID pathUuid = UUID.randomUUID();
        UUID bodyUuid = UUID.randomUUID();

        ProductUpdateRequestDto updateRequestDto = ProductDtoFixtures.aValidUpdateRequest();
        updateRequestDto.setProductUuid(bodyUuid);

        ErrorResponseDto errorResponseDto = ErrorResponseDto.builder()
                .message("Path UUID and body UUID do not match")
                .httpStatusCode(400)
                .errorCodeType(TECHNICAL)
                .build();

        mockMvc.perform(patch(UPDATE_PRODUCT_ENDPOINT, pathUuid)
                        .with(jwtAdmin(ADMIN_KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON)
                        .content(asJsonString(updateRequestDto)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(errorResponseDto), STRICT));
    }

    @Test
    void shouldFailUpdateProductCauseDtoBadRequest() throws Exception {
        UUID productUuid = UUID.randomUUID();
        ProductUpdateRequestDto invalidDto = new ProductUpdateRequestDto();
        invalidDto.setProductUuid(productUuid);
        // brand/category/description are @NotNull → triggers MethodArgumentNotValidException.

        mockMvc.perform(patch(UPDATE_PRODUCT_ENDPOINT, productUuid)
                        .with(jwtAdmin(ADMIN_KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON)
                        .content(asJsonString(invalidDto)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(jsonPath("$.message", startsWith("Validation failed:")))
                .andExpect(jsonPath("$.httpStatusCode").value(400))
                .andExpect(jsonPath("$.errorCodeType").value("TECHNICAL"));
    }

    @Test
    void shouldFailUpdateProductCauseProductNotFound() throws Exception {
        UUID productUuid = UUID.randomUUID();
        ProductUpdateRequestDto updateRequestDto = ProductDtoFixtures.aValidUpdateRequest();
        updateRequestDto.setProductUuid(productUuid);

        ErrorResponseDto errorResponseDto = ErrorResponseDto.builder()
                .message("No product with the UUID : " + productUuid + " found")
                .httpStatusCode(404)
                .errorCodeType(FUNCTIONAL)
                .build();

        when(productService.update(any(ProductUpdateRequestDto.class)))
                .thenThrow(new ProductNotFoundException("No product with the UUID : " + productUuid + " found"));

        mockMvc.perform(patch(UPDATE_PRODUCT_ENDPOINT, productUuid)
                        .with(jwtAdmin(ADMIN_KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON)
                        .content(asJsonString(updateRequestDto)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(errorResponseDto), STRICT));
    }

    @Test
    void shouldRejectUpdateProductWhenAnonymous() throws Exception {
        UUID productUuid = UUID.randomUUID();
        ProductUpdateRequestDto updateRequestDto = ProductDtoFixtures.aValidUpdateRequest();
        updateRequestDto.setProductUuid(productUuid);

        mockMvc.perform(patch(UPDATE_PRODUCT_ENDPOINT, productUuid)
                        .with(jwtAnonymous())
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON)
                        .content(asJsonString(updateRequestDto)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldForbidUpdateProductForNonAdminUser() throws Exception {
        // BUG-031 (closed by F1/F2): non-admin → 403.
        UUID productUuid = UUID.randomUUID();
        ProductUpdateRequestDto updateRequestDto = ProductDtoFixtures.aValidUpdateRequest();
        updateRequestDto.setProductUuid(productUuid);

        ErrorResponseDto errorResponseDto = ErrorResponseDto.builder()
                .message("Access denied")
                .httpStatusCode(403)
                .errorCodeType(FUNCTIONAL)
                .build();

        mockMvc.perform(patch(UPDATE_PRODUCT_ENDPOINT, productUuid)
                        .with(jwtUser(USER_KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON)
                        .content(asJsonString(updateRequestDto)))
                .andExpect(status().isForbidden())
                .andExpect(content().json(asJsonString(errorResponseDto), STRICT));
    }

    @Test
    void shouldRejectUpdateProductWhenPathUuidIsMalformed() throws Exception {
        // BUG-029 (closed by F2): MethodArgumentTypeMismatchException now → 400 TECHNICAL.
        ProductUpdateRequestDto updateRequestDto = ProductDtoFixtures.aValidUpdateRequest();

        ErrorResponseDto errorResponseDto = ErrorResponseDto.builder()
                .message("Invalid value for parameter 'productUuid'")
                .httpStatusCode(400)
                .errorCodeType(TECHNICAL)
                .build();

        mockMvc.perform(patch(UPDATE_PRODUCT_ENDPOINT, "not-a-uuid")
                        .with(jwtAdmin(ADMIN_KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON)
                        .content(asJsonString(updateRequestDto)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(errorResponseDto), STRICT));
    }

    // ----------------------------------------------------------------------
    // DELETE /delete/{productUuid}
    // ----------------------------------------------------------------------

    @Test
    void shouldDeleteProductByUuidAsAdminSuccessfully() throws Exception {
        UUID productUuid = UUID.randomUUID();
        doNothing().when(productService).deleteByUUID(productUuid);

        mockMvc.perform(delete(DELETE_PRODUCT_ENDPOINT, productUuid)
                        .with(jwtAdmin(ADMIN_KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON))
                .andExpect(status().isNoContent());

        verify(productService).deleteByUUID(productUuid);
    }

    @Test
    void shouldFailDeleteProductCauseProductNotFound() throws Exception {
        UUID productUuid = UUID.randomUUID();
        ErrorResponseDto errorResponseDto = ErrorResponseDto.builder()
                .message("No product with the UUID : " + productUuid + " found")
                .httpStatusCode(404)
                .errorCodeType(FUNCTIONAL)
                .build();

        doThrow(new ProductNotFoundException("No product with the UUID : " + productUuid + " found"))
                .when(productService)
                .deleteByUUID(productUuid);

        mockMvc.perform(delete(DELETE_PRODUCT_ENDPOINT, productUuid)
                        .with(jwtAdmin(ADMIN_KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(errorResponseDto), STRICT));
    }

    @Test
    void shouldRejectDeleteProductWhenAnonymous() throws Exception {
        UUID productUuid = UUID.randomUUID();

        mockMvc.perform(delete(DELETE_PRODUCT_ENDPOINT, productUuid)
                        .with(jwtAnonymous())
                        .with(csrf())
                        .accept(APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldForbidDeleteProductForNonAdminUser() throws Exception {
        // BUG-031 (closed by F1/F2): non-admin → 403.
        UUID productUuid = UUID.randomUUID();
        ErrorResponseDto errorResponseDto = ErrorResponseDto.builder()
                .message("Access denied")
                .httpStatusCode(403)
                .errorCodeType(FUNCTIONAL)
                .build();

        mockMvc.perform(delete(DELETE_PRODUCT_ENDPOINT, productUuid)
                        .with(jwtUser(USER_KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(content().json(asJsonString(errorResponseDto), STRICT));
    }

    @Test
    void shouldRejectDeleteProductWhenPathUuidIsMalformed() throws Exception {
        // BUG-029 (closed by F2): bad UUID in path → 400 TECHNICAL.
        ErrorResponseDto errorResponseDto = ErrorResponseDto.builder()
                .message("Invalid value for parameter 'productUuid'")
                .httpStatusCode(400)
                .errorCodeType(TECHNICAL)
                .build();

        mockMvc.perform(delete(DELETE_PRODUCT_ENDPOINT, "not-a-uuid")
                        .with(jwtAdmin(ADMIN_KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(errorResponseDto), STRICT));
    }

    // ----------------------------------------------------------------------
    // POST /create-with-image (multipart)
    // ----------------------------------------------------------------------

    @Test
    void shouldCreateProductWithImageAsAdminSuccessfully() throws Exception {
        ProductCreateRequestDto createRequestDto = ProductDtoFixtures.aValidCreateRequest();
        ProductResponseDto productResponseDto = ProductDtoFixtures.aSampleProductResponse();

        MockMultipartFile productPart = new MockMultipartFile(
                "product",
                "product.json",
                APPLICATION_JSON.toString(),
                asJsonString(createRequestDto).getBytes());

        MockMultipartFile imagePart = new MockMultipartFile(
                "image",
                "image.jpg",
                "image/jpeg",
                "fake-image-bytes".getBytes());

        when(productService.createWithImage(any(ProductCreateRequestDto.class), any()))
                .thenReturn(productResponseDto);

        mockMvc.perform(multipart(CREATE_PRODUCT_WITH_IMAGE_ENDPOINT)
                        .file(productPart)
                        .file(imagePart)
                        .with(jwtAdmin(ADMIN_KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(productResponseDto), STRICT));
    }

    @Test
    void shouldFailCreateProductWithImageWhenImagePartMissing() throws Exception {
        // Missing required @RequestPart("image") → MissingServletRequestPartException. Spring's
        // DefaultHandlerExceptionResolver maps it to 400 before the @ControllerAdvice catch-all
        // sees it. No envelope is produced (resolver writes status only), so just assert the
        // status here.
        ProductCreateRequestDto createRequestDto = ProductDtoFixtures.aValidCreateRequest();

        MockMultipartFile productPart = new MockMultipartFile(
                "product",
                "product.json",
                APPLICATION_JSON.toString(),
                asJsonString(createRequestDto).getBytes());

        mockMvc.perform(multipart(CREATE_PRODUCT_WITH_IMAGE_ENDPOINT)
                        .file(productPart)
                        .with(jwtAdmin(ADMIN_KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectCreateProductWithImageWhenAnonymous() throws Exception {
        ProductCreateRequestDto createRequestDto = ProductDtoFixtures.aValidCreateRequest();

        MockMultipartFile productPart = new MockMultipartFile(
                "product",
                "product.json",
                APPLICATION_JSON.toString(),
                asJsonString(createRequestDto).getBytes());

        MockMultipartFile imagePart = new MockMultipartFile(
                "image",
                "image.jpg",
                "image/jpeg",
                "fake-image-bytes".getBytes());

        mockMvc.perform(multipart(CREATE_PRODUCT_WITH_IMAGE_ENDPOINT)
                        .file(productPart)
                        .file(imagePart)
                        .with(jwtAnonymous())
                        .with(csrf())
                        .accept(APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldForbidCreateProductWithImageForNonAdminUser() throws Exception {
        // BUG-031 (closed by F1/F2): non-admin → 403.
        ProductCreateRequestDto createRequestDto = ProductDtoFixtures.aValidCreateRequest();

        MockMultipartFile productPart = new MockMultipartFile(
                "product",
                "product.json",
                APPLICATION_JSON.toString(),
                asJsonString(createRequestDto).getBytes());

        MockMultipartFile imagePart = new MockMultipartFile(
                "image",
                "image.jpg",
                "image/jpeg",
                "fake-image-bytes".getBytes());

        ErrorResponseDto errorResponseDto = ErrorResponseDto.builder()
                .message("Access denied")
                .httpStatusCode(403)
                .errorCodeType(FUNCTIONAL)
                .build();

        mockMvc.perform(multipart(CREATE_PRODUCT_WITH_IMAGE_ENDPOINT)
                        .file(productPart)
                        .file(imagePart)
                        .with(jwtUser(USER_KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(content().json(asJsonString(errorResponseDto), STRICT));
    }
}
