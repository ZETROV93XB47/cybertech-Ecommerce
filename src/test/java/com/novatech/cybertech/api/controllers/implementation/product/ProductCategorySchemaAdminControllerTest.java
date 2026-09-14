package com.novatech.cybertech.api.controllers.implementation.product;

import com.novatech.cybertech.api.controllers.TestSecurityConfig;
import com.novatech.cybertech.api.controllers.implementation.ProductCategorySchemaAdminController;
import com.novatech.cybertech.dto.request.product.ProductCategorySchemaCreateRequestDto;
import com.novatech.cybertech.dto.request.product.ProductCategorySchemaUpdateRequestDto;
import com.novatech.cybertech.dto.response.product.ProductCategorySchemaResponseDto;
import com.novatech.cybertech.exceptions.InvalidProductCategorySchemaException;
import com.novatech.cybertech.exceptions.UnknownProductCategoryException;
import com.novatech.cybertech.services.core.ProductCategorySchemaService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static com.novatech.cybertech.fixtures.support.JwtTestUtils.jwtAdmin;
import static com.novatech.cybertech.fixtures.support.JwtTestUtils.jwtAnonymous;
import static com.novatech.cybertech.fixtures.support.JwtTestUtils.jwtUser;
import static com.novatech.cybertech.utils.TestUtils.asJsonString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.json.JsonCompareMode.STRICT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Slf4j
@Import({TestSecurityConfig.class})
@WebMvcTest(value = ProductCategorySchemaAdminController.class)
class ProductCategorySchemaAdminControllerTest {

    private static final String BASE = "/api/v1/services/admin/product-category-schemas";
    private static final String GET_ALL_ENDPOINT = BASE + "/get/all";
    private static final String GET_BY_KEY_ENDPOINT = BASE + "/get/{categoryKey}";
    private static final String CREATE_ENDPOINT = BASE + "/create";
    private static final String UPDATE_ENDPOINT = BASE + "/update/{categoryKey}";
    private static final String DELETE_ENDPOINT = BASE + "/delete/{categoryKey}";

    private static final String ADMIN_KEYCLOAK_ID = "admin-keycloak-id";
    private static final String USER_KEYCLOAK_ID = "user-keycloak-id";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ProductCategorySchemaService productCategorySchemaService;

    private ProductCategorySchemaResponseDto sampleResponse() {
        return ProductCategorySchemaResponseDto.builder()
                .categoryKey("COMPUTER")
                .label("Computers")
                .jsonSchema("{\"type\":\"object\"}")
                .active(true)
                .build();
    }

    // ----------------------------------------------------------------------
    // GET /get/all
    // ----------------------------------------------------------------------

    @Test
    void shouldGetAllAsAdmin() throws Exception {
        when(productCategorySchemaService.getAll()).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get(GET_ALL_ENDPOINT)
                        .with(jwtAdmin(ADMIN_KEYCLOAK_ID))
                        .accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().json(asJsonString(List.of(sampleResponse())), STRICT));
    }

    @Test
    void shouldRejectGetAllWhenAnonymous() throws Exception {
        mockMvc.perform(get(GET_ALL_ENDPOINT).with(jwtAnonymous()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldForbidGetAllForNonAdmin() throws Exception {
        mockMvc.perform(get(GET_ALL_ENDPOINT).with(jwtUser(USER_KEYCLOAK_ID)))
                .andExpect(status().isForbidden());
    }

    // ----------------------------------------------------------------------
    // GET /get/{categoryKey}
    // ----------------------------------------------------------------------

    @Test
    void shouldGetByCategoryKeyAsAdmin() throws Exception {
        when(productCategorySchemaService.getByCategoryKey("COMPUTER")).thenReturn(sampleResponse());

        mockMvc.perform(get(GET_BY_KEY_ENDPOINT, "COMPUTER")
                        .with(jwtAdmin(ADMIN_KEYCLOAK_ID))
                        .accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().json(asJsonString(sampleResponse()), STRICT));
    }

    @Test
    void shouldFailGetByCategoryKeyWhenUnknown() throws Exception {
        when(productCategorySchemaService.getByCategoryKey("TABLET"))
                .thenThrow(new UnknownProductCategoryException("Unknown product category: TABLET"));

        mockMvc.perform(get(GET_BY_KEY_ENDPOINT, "TABLET")
                        .with(jwtAdmin(ADMIN_KEYCLOAK_ID))
                        .accept(APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    // ----------------------------------------------------------------------
    // POST /create
    // ----------------------------------------------------------------------

    @Test
    void shouldCreateAsAdmin() throws Exception {
        ProductCategorySchemaCreateRequestDto request =
                new ProductCategorySchemaCreateRequestDto("COMPUTER", "Computers", "{\"type\":\"object\"}");
        when(productCategorySchemaService.create(any(ProductCategorySchemaCreateRequestDto.class)))
                .thenReturn(sampleResponse());

        mockMvc.perform(post(CREATE_ENDPOINT)
                        .with(jwtAdmin(ADMIN_KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON)
                        .content(asJsonString(request)))
                .andExpect(status().isCreated())
                .andExpect(content().json(asJsonString(sampleResponse()), STRICT));
    }

    @Test
    void shouldFailCreateWhenSchemaMalformed() throws Exception {
        ProductCategorySchemaCreateRequestDto request =
                new ProductCategorySchemaCreateRequestDto("COMPUTER", "Computers", "{not valid json");
        when(productCategorySchemaService.create(any(ProductCategorySchemaCreateRequestDto.class)))
                .thenThrow(new InvalidProductCategorySchemaException("Malformed JSON Schema"));

        mockMvc.perform(post(CREATE_ENDPOINT)
                        .with(jwtAdmin(ADMIN_KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON)
                        .content(asJsonString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectCreateWhenAnonymous() throws Exception {
        ProductCategorySchemaCreateRequestDto request =
                new ProductCategorySchemaCreateRequestDto("COMPUTER", "Computers", "{\"type\":\"object\"}");

        mockMvc.perform(post(CREATE_ENDPOINT)
                        .with(jwtAnonymous())
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content(asJsonString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldForbidCreateForNonAdmin() throws Exception {
        ProductCategorySchemaCreateRequestDto request =
                new ProductCategorySchemaCreateRequestDto("COMPUTER", "Computers", "{\"type\":\"object\"}");

        mockMvc.perform(post(CREATE_ENDPOINT)
                        .with(jwtUser(USER_KEYCLOAK_ID))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content(asJsonString(request)))
                .andExpect(status().isForbidden());
    }

    // ----------------------------------------------------------------------
    // PATCH /update/{categoryKey}
    // ----------------------------------------------------------------------

    @Test
    void shouldUpdateAsAdmin() throws Exception {
        ProductCategorySchemaUpdateRequestDto request =
                new ProductCategorySchemaUpdateRequestDto("Computers v2", null, null);
        when(productCategorySchemaService.update(eq("COMPUTER"), any(ProductCategorySchemaUpdateRequestDto.class)))
                .thenReturn(sampleResponse());

        mockMvc.perform(patch(UPDATE_ENDPOINT, "COMPUTER")
                        .with(jwtAdmin(ADMIN_KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON)
                        .content(asJsonString(request)))
                .andExpect(status().isOk())
                .andExpect(content().json(asJsonString(sampleResponse()), STRICT));
    }

    @Test
    void shouldFailUpdateWhenUnknownCategory() throws Exception {
        ProductCategorySchemaUpdateRequestDto request = new ProductCategorySchemaUpdateRequestDto("x", null, null);
        when(productCategorySchemaService.update(eq("TABLET"), any(ProductCategorySchemaUpdateRequestDto.class)))
                .thenThrow(new UnknownProductCategoryException("Unknown product category: TABLET"));

        mockMvc.perform(patch(UPDATE_ENDPOINT, "TABLET")
                        .with(jwtAdmin(ADMIN_KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON)
                        .content(asJsonString(request)))
                .andExpect(status().isBadRequest());
    }

    // ----------------------------------------------------------------------
    // DELETE /delete/{categoryKey}
    // ----------------------------------------------------------------------

    @Test
    void shouldDeleteAsAdmin() throws Exception {
        doNothing().when(productCategorySchemaService).deleteByCategoryKey("COMPUTER");

        mockMvc.perform(delete(DELETE_ENDPOINT, "COMPUTER")
                        .with(jwtAdmin(ADMIN_KEYCLOAK_ID))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(productCategorySchemaService).deleteByCategoryKey("COMPUTER");
    }

    @Test
    void shouldFailDeleteWhenUnknownCategory() throws Exception {
        doThrow(new UnknownProductCategoryException("Unknown product category: TABLET"))
                .when(productCategorySchemaService).deleteByCategoryKey("TABLET");

        mockMvc.perform(delete(DELETE_ENDPOINT, "TABLET")
                        .with(jwtAdmin(ADMIN_KEYCLOAK_ID))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectDeleteWhenAnonymous() throws Exception {
        mockMvc.perform(delete(DELETE_ENDPOINT, "COMPUTER")
                        .with(jwtAnonymous())
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldForbidDeleteForNonAdmin() throws Exception {
        mockMvc.perform(delete(DELETE_ENDPOINT, "COMPUTER")
                        .with(jwtUser(USER_KEYCLOAK_ID))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }
}
