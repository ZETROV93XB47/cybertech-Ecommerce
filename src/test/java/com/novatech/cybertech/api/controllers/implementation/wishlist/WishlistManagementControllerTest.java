package com.novatech.cybertech.api.controllers.implementation.wishlist;

import com.novatech.cybertech.api.controllers.TestSecurityConfig;
import com.novatech.cybertech.api.controllers.implementation.WishlistManagementController;
import com.novatech.cybertech.api.error.model.ErrorResponseDto;
import com.novatech.cybertech.dto.response.wishlist.WishlistResponseDto;
import com.novatech.cybertech.exceptions.ProductAlreadyInWishlist;
import com.novatech.cybertech.exceptions.ProductNotFoundException;
import com.novatech.cybertech.exceptions.WishlistNotFoundException;
import com.novatech.cybertech.fixtures.dto.WishlistDtoFixtures;
import com.novatech.cybertech.fixtures.support.JwtTestUtils;
import com.novatech.cybertech.services.core.WishlistService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static com.novatech.cybertech.api.error.enumpackage.ErrorCodeType.FUNCTIONAL;
import static com.novatech.cybertech.api.error.enumpackage.ErrorCodeType.TECHNICAL;
import static com.novatech.cybertech.utils.TestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Slf4j
@Import({TestSecurityConfig.class})
@WebMvcTest(value = WishlistManagementController.class)
class WishlistManagementControllerTest {

    private static final String ADD_TO_WISHLIST_ENDPOINT = "/api/v1/services/wishlist/add/{productUuid}";
    private static final String REMOVE_FROM_WISHLIST_ENDPOINT = "/api/v1/services/wishlist/remove/{productUuid}";
    private static final String GET_MY_WISHLIST_ENDPOINT = "/api/v1/services/wishlist/my-wishlist";

    private static final String KEYCLOAK_ID = "keycloakId";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    WishlistService wishlistService;

    // -----------------------------------------------------------------
    // POST /add/{productUuid}
    // -----------------------------------------------------------------
    @Test
    void shouldAddProductToWishlistSuccessfully() throws Exception {
        UUID productUuid = UUID.randomUUID();
        WishlistResponseDto response = WishlistDtoFixtures.aSampleWishlistResponse();

        when(wishlistService.addProductToMyWishlist(anyString(), eq(productUuid))).thenReturn(response);

        mockMvc.perform(post(ADD_TO_WISHLIST_ENDPOINT, productUuid)
                        .with(JwtTestUtils.jwtUser(KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(response), STRICT));
    }

    @Test
    void shouldAddProductToWishlist_forwardsJwtSubjectAsString() throws Exception {
        UUID productUuid = UUID.randomUUID();
        WishlistResponseDto response = WishlistDtoFixtures.aSampleWishlistResponse();
        when(wishlistService.addProductToMyWishlist(anyString(), any(UUID.class))).thenReturn(response);

        mockMvc.perform(post(ADD_TO_WISHLIST_ENDPOINT, productUuid)
                        .with(JwtTestUtils.jwtUser(KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON))
                .andExpect(status().isCreated());

        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<UUID> uuidCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(wishlistService).addProductToMyWishlist(subjectCaptor.capture(), uuidCaptor.capture());
        assertThat(subjectCaptor.getValue()).isEqualTo(KEYCLOAK_ID);
        assertThat(uuidCaptor.getValue()).isEqualTo(productUuid);
    }

    @Test
    void failAddProductToWishlist_whenAlreadyInWishlist_thenConflict() throws Exception {
        UUID productUuid = UUID.randomUUID();
        String message = "Product already in wishlist";
        ErrorResponseDto errorResponseDto = ErrorResponseDto.builder()
                .message(message)
                .httpStatusCode(409)
                .errorCodeType(FUNCTIONAL)
                .build();

        when(wishlistService.addProductToMyWishlist(anyString(), eq(productUuid)))
                .thenThrow(new ProductAlreadyInWishlist(message));

        mockMvc.perform(post(ADD_TO_WISHLIST_ENDPOINT, productUuid)
                        .with(JwtTestUtils.jwtUser(KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(errorResponseDto), STRICT));
    }

    @Test
    void failAddProductToWishlist_whenProductNotFound_thenNotFound() throws Exception {
        UUID productUuid = UUID.randomUUID();
        String message = "Product " + productUuid + " not found";
        ErrorResponseDto errorResponseDto = ErrorResponseDto.builder()
                .message(message)
                .httpStatusCode(404)
                .errorCodeType(FUNCTIONAL)
                .build();

        when(wishlistService.addProductToMyWishlist(anyString(), eq(productUuid)))
                .thenThrow(new ProductNotFoundException(message));

        mockMvc.perform(post(ADD_TO_WISHLIST_ENDPOINT, productUuid)
                        .with(JwtTestUtils.jwtUser(KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(errorResponseDto), STRICT));
    }

    @Test
    void failAddProduct_whenInvalidUuidPath_thenBadRequest() throws Exception {
        // BUG-029 fixed in Wave F2: MethodArgumentTypeMismatchException handler now returns 400.
        ErrorResponseDto errorResponseDto = ErrorResponseDto.builder()
                .message("Invalid value for parameter 'productUuid'")
                .httpStatusCode(400)
                .errorCodeType(TECHNICAL)
                .build();

        mockMvc.perform(post("/api/v1/services/wishlist/add/not-a-uuid")
                        .with(JwtTestUtils.jwtUser(KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(errorResponseDto), STRICT));
    }

    // -----------------------------------------------------------------
    // DELETE /remove/{productUuid}
    // -----------------------------------------------------------------
    @Test
    void shouldRemoveProductFromWishlistSuccessfully() throws Exception {
        UUID productUuid = UUID.randomUUID();
        doNothing().when(wishlistService).removeProductFromMyWishlist(anyString(), eq(productUuid));

        mockMvc.perform(delete(REMOVE_FROM_WISHLIST_ENDPOINT, productUuid)
                        .with(JwtTestUtils.jwtUser(KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON))
                .andExpect(status().isNoContent());

        verify(wishlistService).removeProductFromMyWishlist(KEYCLOAK_ID, productUuid);
    }

    @Test
    void shouldRemoveProductFromWishlist_forwardsJwtSubjectAsString() throws Exception {
        UUID productUuid = UUID.randomUUID();
        doNothing().when(wishlistService).removeProductFromMyWishlist(anyString(), any(UUID.class));

        mockMvc.perform(delete(REMOVE_FROM_WISHLIST_ENDPOINT, productUuid)
                        .with(JwtTestUtils.jwtUser(KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON))
                .andExpect(status().isNoContent());

        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<UUID> uuidCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(wishlistService).removeProductFromMyWishlist(subjectCaptor.capture(), uuidCaptor.capture());
        assertThat(subjectCaptor.getValue()).isEqualTo(KEYCLOAK_ID);
        assertThat(uuidCaptor.getValue()).isEqualTo(productUuid);
    }

    @Test
    void failRemoveProductFromWishlist_whenWishlistNotFound_thenNotFound() throws Exception {
        UUID productUuid = UUID.randomUUID();
        String message = "Wishlist entry not found";
        ErrorResponseDto errorResponseDto = ErrorResponseDto.builder()
                .message(message)
                .httpStatusCode(404)
                .errorCodeType(FUNCTIONAL)
                .build();

        doThrow(new WishlistNotFoundException(message))
                .when(wishlistService).removeProductFromMyWishlist(anyString(), eq(productUuid));

        mockMvc.perform(delete(REMOVE_FROM_WISHLIST_ENDPOINT, productUuid)
                        .with(JwtTestUtils.jwtUser(KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(errorResponseDto), STRICT));
    }

    @Test
    void failRemoveProduct_whenInvalidUuidPath_thenBadRequest() throws Exception {
        // BUG-029 fixed in Wave F2.
        ErrorResponseDto errorResponseDto = ErrorResponseDto.builder()
                .message("Invalid value for parameter 'productUuid'")
                .httpStatusCode(400)
                .errorCodeType(TECHNICAL)
                .build();

        mockMvc.perform(delete("/api/v1/services/wishlist/remove/not-a-uuid")
                        .with(JwtTestUtils.jwtUser(KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(errorResponseDto), STRICT));
    }

    // -----------------------------------------------------------------
    // GET /my-wishlist
    // -----------------------------------------------------------------
    @Test
    void shouldGetMyWishlistSuccessfully() throws Exception {
        WishlistResponseDto entry = WishlistDtoFixtures.aSampleWishlistResponse();
        Page<WishlistResponseDto> page = new PageImpl<>(List.of(entry), PageRequest.of(0, 20), 1);

        when(wishlistService.getMyWishlist(anyString(), any(Pageable.class))).thenReturn(page);

        // Page<T> is serialized as a non-stable PageImpl JSON (tech-debt noted in SA2.2).
        // Use jsonPath rather than STRICT equality to avoid coupling to PageImpl's internal shape.
        mockMvc.perform(get(GET_MY_WISHLIST_ENDPOINT)
                        .with(JwtTestUtils.jwtUser(KEYCLOAK_ID))
                        .accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].uuid").value(entry.getUuid().toString()))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void shouldGetMyWishlist_forwardsJwtSubjectAsStringAndPageable() throws Exception {
        Page<WishlistResponseDto> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(wishlistService.getMyWishlist(anyString(), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get(GET_MY_WISHLIST_ENDPOINT)
                        .with(JwtTestUtils.jwtUser(KEYCLOAK_ID))
                        .accept(APPLICATION_JSON))
                .andExpect(status().isOk());

        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(wishlistService).getMyWishlist(subjectCaptor.capture(), pageableCaptor.capture());
        assertThat(subjectCaptor.getValue()).isEqualTo(KEYCLOAK_ID);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(20);
    }

    // -----------------------------------------------------------------
    // Authn / Authz cross-cutting
    // -----------------------------------------------------------------
    @Test
    void whenAnonymousAddToWishlist_thenUnauthorized() throws Exception {
        // BUG-030 fixed by W0: TestSecurityConfig now returns 401 for anonymous.
        UUID productUuid = UUID.randomUUID();
        mockMvc.perform(post(ADD_TO_WISHLIST_ENDPOINT, productUuid)
                        .with(csrf())
                        .accept(APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void whenAnonymousGetMyWishlist_thenUnauthorized() throws Exception {
        mockMvc.perform(get(GET_MY_WISHLIST_ENDPOINT)
                        .accept(APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void whenAnonymousRemoveFromWishlist_thenUnauthorized() throws Exception {
        UUID productUuid = UUID.randomUUID();
        mockMvc.perform(delete(REMOVE_FROM_WISHLIST_ENDPOINT, productUuid)
                        .with(csrf())
                        .accept(APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void whenAdminAuthenticated_thenAllowedOnIsAuthenticatedEndpoints() throws Exception {
        // @PreAuthorize("isAuthenticated()") accepts ROLE_ADMIN as well.
        UUID productUuid = UUID.randomUUID();
        WishlistResponseDto response = WishlistDtoFixtures.aSampleWishlistResponse();
        when(wishlistService.addProductToMyWishlist(anyString(), eq(productUuid))).thenReturn(response);

        mockMvc.perform(post(ADD_TO_WISHLIST_ENDPOINT, productUuid)
                        .with(JwtTestUtils.jwtAdmin("adminId"))
                        .with(csrf())
                        .accept(APPLICATION_JSON))
                .andExpect(status().isCreated());
    }
}
