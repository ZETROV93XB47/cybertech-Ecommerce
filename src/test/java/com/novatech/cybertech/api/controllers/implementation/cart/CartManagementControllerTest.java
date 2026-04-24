package com.novatech.cybertech.api.controllers.implementation.cart;

import com.novatech.cybertech.api.controllers.TestSecurityConfig;
import com.novatech.cybertech.api.controllers.implementation.CartManagementController;
import com.novatech.cybertech.api.error.model.ErrorResponseDto;
import com.novatech.cybertech.dto.request.cart.CartCreateRequestDto;
import com.novatech.cybertech.dto.request.cart.CartItemAddRequestDto;
import com.novatech.cybertech.dto.request.cart.CartItemRemoveRequestDto;
import com.novatech.cybertech.dto.request.cart.CartUpdateRequestDto;
import com.novatech.cybertech.dto.response.cart.CartResponseDto;
import com.novatech.cybertech.exceptions.CartNotFoundException;
import com.novatech.cybertech.exceptions.NotEnoughStockException;
import com.novatech.cybertech.exceptions.UnauthorizedCartAccessException;
import com.novatech.cybertech.fixtures.dto.CartDtoFixtures;
import com.novatech.cybertech.fixtures.support.JwtTestUtils;
import com.novatech.cybertech.services.core.CartService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static com.novatech.cybertech.api.error.enumpackage.ErrorCodeType.FUNCTIONAL;
import static com.novatech.cybertech.api.error.enumpackage.ErrorCodeType.TECHNICAL;
import static com.novatech.cybertech.utils.TestUtils.asJsonString;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Import({TestSecurityConfig.class})
@WebMvcTest(value = CartManagementController.class)
class CartManagementControllerTest {

    private static final String GET_CART_BY_UUID_ENDPOINT = "/api/v1/services/cart/get/{cartUuid}";
    private static final String CREATE_CART_ENDPOINT = "/api/v1/services/cart/create";
    private static final String UPDATE_CART_ENDPOINT = "/api/v1/services/cart/update/{cartUuid}";
    private static final String DELETE_CART_ENDPOINT = "/api/v1/services/cart/delete/{cartUuid}";
    private static final String GET_MY_CART_ENDPOINT = "/api/v1/services/cart/get";
    private static final String CLEAR_CART_ENDPOINT = "/api/v1/services/cart/clear";
    private static final String ADD_TO_CART_ENDPOINT = "/api/v1/services/cart/add";
    private static final String REMOVE_FROM_CART_ENDPOINT = "/api/v1/services/cart/remove/{productUuid}";
    private static final String DECREASE_QUANTITY_ENDPOINT = "/api/v1/services/cart/decreaseQuantity";

    private static final String KEYCLOAK_ID = "keycloakId";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    CartService cartService;

    // -----------------------------------------------------------------
    // CRUD endpoints
    // -----------------------------------------------------------------
    @Nested
    class CartCRUD {

        @Test
        void shouldGetCartByUuidSuccessfully() throws Exception {
            UUID cartUuid = UUID.randomUUID();
            CartResponseDto response = CartDtoFixtures.aSampleCartResponseBuilder().cartUuid(cartUuid).build();

            // BUG-161 fix: controller now forwards JWT subject to ownership-checked overload.
            when(cartService.getByUUID(eq(cartUuid), eq(KEYCLOAK_ID))).thenReturn(response);

            mockMvc.perform(get(GET_CART_BY_UUID_ENDPOINT, cartUuid)
                            .with(JwtTestUtils.jwtUser(KEYCLOAK_ID))
                            .contentType(APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(APPLICATION_JSON))
                    .andExpect(content().json(asJsonString(response), STRICT));
        }

        @Test
        void failGetCartByUuid_whenCartNotFound_thenNotFound() throws Exception {
            // BUG-025 fixed in Wave F2: handler now maps CartNotFoundException -> 404 CART_NOT_FOUND.
            UUID cartUuid = UUID.randomUUID();
            String message = "No cart with the UUID : " + cartUuid + " found";

            ErrorResponseDto errorResponseDto = ErrorResponseDto.builder()
                    .message(message)
                    .httpStatusCode(404)
                    .errorCodeType(FUNCTIONAL)
                    .build();

            when(cartService.getByUUID(eq(cartUuid), eq(KEYCLOAK_ID))).thenThrow(new CartNotFoundException(message));

            mockMvc.perform(get(GET_CART_BY_UUID_ENDPOINT, cartUuid)
                            .with(JwtTestUtils.jwtUser(KEYCLOAK_ID))
                            .contentType(APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentType(APPLICATION_JSON))
                    .andExpect(content().json(asJsonString(errorResponseDto), STRICT));
        }

        @Test
        void shouldCreateCartSuccessfully() throws Exception {
            CartCreateRequestDto request = CartDtoFixtures.aValidCartCreateRequest();
            CartResponseDto response = CartDtoFixtures.aSampleCartResponse();

            when(cartService.create(any(CartCreateRequestDto.class))).thenReturn(response);

            mockMvc.perform(post(CREATE_CART_ENDPOINT)
                            .with(JwtTestUtils.jwtUser(KEYCLOAK_ID))
                            .with(csrf())
                            .accept(APPLICATION_JSON)
                            .contentType(APPLICATION_JSON)
                            .content(asJsonString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(content().contentType(APPLICATION_JSON))
                    .andExpect(content().json(asJsonString(response), STRICT));
        }

        @Test
        void failCreateCart_whenNullCartItems_thenBadRequest() throws Exception {
            // Outer DTO @NotNull on cartItemAddRequestDtos
            CartCreateRequestDto bad = new CartCreateRequestDto();

            mockMvc.perform(post(CREATE_CART_ENDPOINT)
                            .with(JwtTestUtils.jwtUser(KEYCLOAK_ID))
                            .with(csrf())
                            .accept(APPLICATION_JSON)
                            .contentType(APPLICATION_JSON)
                            .content(asJsonString(bad)))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(APPLICATION_JSON))
                    .andExpect(jsonPath("$.message", startsWith("Validation failed:")))
                    .andExpect(jsonPath("$.httpStatusCode").value(400))
                    .andExpect(jsonPath("$.errorCodeType").value("TECHNICAL"));
        }

        @Test
        void failCreateCart_whenNestedNegativeQuantity_thenBadRequest() throws Exception {
            // BUG-028 fixed in Wave F2: @Valid on cartItemAddRequestDtos now propagates to nested @Min(1).
            CartItemAddRequestDto badItem = CartItemAddRequestDto.builder()
                    .productUuid(UUID.randomUUID())
                    .quantity(-5)
                    .build();
            CartCreateRequestDto request = CartCreateRequestDto.builder()
                    .cartItemAddRequestDtos(List.of(badItem))
                    .build();

            mockMvc.perform(post(CREATE_CART_ENDPOINT)
                            .with(JwtTestUtils.jwtUser(KEYCLOAK_ID))
                            .with(csrf())
                            .accept(APPLICATION_JSON)
                            .contentType(APPLICATION_JSON)
                            .content(asJsonString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(APPLICATION_JSON))
                    .andExpect(jsonPath("$.message", startsWith("Validation failed:")))
                    .andExpect(jsonPath("$.httpStatusCode").value(400))
                    .andExpect(jsonPath("$.errorCodeType").value("TECHNICAL"));
        }

        @Test
        void shouldUpdateCartSuccessfully() throws Exception {
            // BUG-026 (CLOSED): updateCart now uses CartUpdateRequestDto + JWT subject for BUG-161 ownership.
            UUID cartUuid = UUID.randomUUID();
            CartUpdateRequestDto request = CartUpdateRequestDto.builder()
                    .cartItemAddRequestDtos(List.of(CartDtoFixtures.aValidCartItemAddRequest()))
                    .build();
            CartResponseDto response = CartDtoFixtures.aSampleCartResponseBuilder().cartUuid(cartUuid).build();

            when(cartService.updateCart(eq(cartUuid), any(CartUpdateRequestDto.class), eq(KEYCLOAK_ID)))
                    .thenReturn(response);

            mockMvc.perform(patch(UPDATE_CART_ENDPOINT, cartUuid)
                            .with(JwtTestUtils.jwtUser(KEYCLOAK_ID))
                            .with(csrf())
                            .accept(APPLICATION_JSON)
                            .contentType(APPLICATION_JSON)
                            .content(asJsonString(request)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(APPLICATION_JSON))
                    .andExpect(content().json(asJsonString(response), STRICT));
        }

        @Test
        void failUpdateCart_whenNullBody_thenBadRequest() throws Exception {
            // BUG-026 (CLOSED): empty body (no cartItemAddRequestDtos) trips @NotNull on CartUpdateRequestDto -> 400.
            UUID cartUuid = UUID.randomUUID();
            CartUpdateRequestDto bad = new CartUpdateRequestDto();

            mockMvc.perform(patch(UPDATE_CART_ENDPOINT, cartUuid)
                            .with(JwtTestUtils.jwtUser(KEYCLOAK_ID))
                            .with(csrf())
                            .accept(APPLICATION_JSON)
                            .contentType(APPLICATION_JSON)
                            .content(asJsonString(bad)))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(APPLICATION_JSON))
                    .andExpect(jsonPath("$.message", startsWith("Validation failed:")))
                    .andExpect(jsonPath("$.httpStatusCode").value(400))
                    .andExpect(jsonPath("$.errorCodeType").value("TECHNICAL"));
        }

        @Test
        void shouldDeleteCartByUuidSuccessfully() throws Exception {
            // BUG-027 fixed in Wave F2: deleteCartByUuid now binds @PathVariable("cartUuid").
            // BUG-161 (CLOSED): controller forwards JWT subject for ownership check.
            UUID cartUuid = UUID.randomUUID();
            doNothing().when(cartService).deleteByUUID(eq(cartUuid), eq(KEYCLOAK_ID));

            mockMvc.perform(delete(DELETE_CART_ENDPOINT, cartUuid)
                            .with(JwtTestUtils.jwtUser(KEYCLOAK_ID))
                            .with(csrf())
                            .accept(APPLICATION_JSON)
                            .contentType(APPLICATION_JSON))
                    .andExpect(status().isNoContent());

            verify(cartService).deleteByUUID(eq(cartUuid), eq(KEYCLOAK_ID));
        }

        @Test
        void idorOnDeleteByCartUuidReturnsForbidden() throws Exception {
            // BUG-161 (CLOSED): caller does not own cart -> service throws
            // UnauthorizedCartAccessException -> @ExceptionHandler -> HTTP 403.
            UUID otherUserCartUuid = UUID.randomUUID();
            doThrow(new UnauthorizedCartAccessException("Caller does not own cart " + otherUserCartUuid))
                    .when(cartService).deleteByUUID(eq(otherUserCartUuid), eq(KEYCLOAK_ID));

            mockMvc.perform(delete(DELETE_CART_ENDPOINT, otherUserCartUuid)
                            .with(JwtTestUtils.jwtUser(KEYCLOAK_ID))
                            .with(csrf())
                            .accept(APPLICATION_JSON)
                            .contentType(APPLICATION_JSON))
                    .andExpect(status().isForbidden());
        }

        @Test
        void idorOnGetByCartUuidReturnsForbidden() throws Exception {
            // BUG-161 (CLOSED): GET /cart/get/{cartUuid} also enforces ownership.
            UUID otherUserCartUuid = UUID.randomUUID();
            when(cartService.getByUUID(eq(otherUserCartUuid), eq(KEYCLOAK_ID)))
                    .thenThrow(new UnauthorizedCartAccessException("Caller does not own cart " + otherUserCartUuid));

            mockMvc.perform(get(GET_CART_BY_UUID_ENDPOINT, otherUserCartUuid)
                            .with(JwtTestUtils.jwtUser(KEYCLOAK_ID))
                            .accept(APPLICATION_JSON)
                            .contentType(APPLICATION_JSON))
                    .andExpect(status().isForbidden());
        }
    }

    // -----------------------------------------------------------------
    // User-facing operations
    // -----------------------------------------------------------------
    @Nested
    class CartUserOps {

        @Test
        void shouldGetCartSuccessfully() throws Exception {
            CartResponseDto response = CartDtoFixtures.aSampleCartResponse();
            when(cartService.getCart(KEYCLOAK_ID)).thenReturn(response);

            mockMvc.perform(get(GET_MY_CART_ENDPOINT)
                            .with(JwtTestUtils.jwtUser(KEYCLOAK_ID))
                            .accept(APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(APPLICATION_JSON))
                    .andExpect(content().json(asJsonString(response), STRICT));
        }

        @Test
        void shouldGetCart_forwardsJwtSubjectAsString() throws Exception {
            CartResponseDto response = CartDtoFixtures.aSampleCartResponse();
            when(cartService.getCart(anyString())).thenReturn(response);

            mockMvc.perform(get(GET_MY_CART_ENDPOINT)
                            .with(JwtTestUtils.jwtUser(KEYCLOAK_ID))
                            .accept(APPLICATION_JSON))
                    .andExpect(status().isOk());

            ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
            verify(cartService).getCart(subjectCaptor.capture());
            assertThat(subjectCaptor.getValue()).isEqualTo(KEYCLOAK_ID);
        }

        @Test
        void shouldClearCartSuccessfully() throws Exception {
            doNothing().when(cartService).clearCart(KEYCLOAK_ID);

            mockMvc.perform(delete(CLEAR_CART_ENDPOINT)
                            .with(JwtTestUtils.jwtUser(KEYCLOAK_ID))
                            .with(csrf())
                            .accept(APPLICATION_JSON))
                    .andExpect(status().isNoContent());

            verify(cartService).clearCart(KEYCLOAK_ID);
        }

        @Test
        void shouldAddToCartSuccessfully() throws Exception {
            CartCreateRequestDto request = CartDtoFixtures.aValidCartCreateRequest();
            CartResponseDto response = CartDtoFixtures.aSampleCartResponse();

            when(cartService.addItemsToCart(any(CartCreateRequestDto.class), anyString())).thenReturn(response);

            mockMvc.perform(post(ADD_TO_CART_ENDPOINT)
                            .with(JwtTestUtils.jwtUser(KEYCLOAK_ID))
                            .with(csrf())
                            .accept(APPLICATION_JSON)
                            .contentType(APPLICATION_JSON)
                            .content(asJsonString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(content().contentType(APPLICATION_JSON))
                    .andExpect(content().json(asJsonString(response), STRICT));
        }

        @Test
        void shouldAddToCart_forwardsJwtSubjectAsString() throws Exception {
            CartCreateRequestDto request = CartDtoFixtures.aValidCartCreateRequest();
            CartResponseDto response = CartDtoFixtures.aSampleCartResponse();
            when(cartService.addItemsToCart(any(CartCreateRequestDto.class), anyString())).thenReturn(response);

            mockMvc.perform(post(ADD_TO_CART_ENDPOINT)
                            .with(JwtTestUtils.jwtUser(KEYCLOAK_ID))
                            .with(csrf())
                            .accept(APPLICATION_JSON)
                            .contentType(APPLICATION_JSON)
                            .content(asJsonString(request)))
                    .andExpect(status().isCreated());

            ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<CartCreateRequestDto> dtoCaptor = ArgumentCaptor.forClass(CartCreateRequestDto.class);
            verify(cartService).addItemsToCart(dtoCaptor.capture(), subjectCaptor.capture());
            assertThat(subjectCaptor.getValue()).isEqualTo(KEYCLOAK_ID);
            assertThat(dtoCaptor.getValue().getCartItemAddRequestDtos()).hasSize(1);
        }

        @Test
        void failAddToCart_whenNegativeQuantity_thenBadRequest() throws Exception {
            // BUG-028 fixed: nested @Valid now fires for negative quantities.
            CartItemAddRequestDto badItem = CartItemAddRequestDto.builder()
                    .productUuid(UUID.randomUUID())
                    .quantity(-1)
                    .build();
            CartCreateRequestDto request = CartCreateRequestDto.builder()
                    .cartItemAddRequestDtos(List.of(badItem))
                    .build();

            mockMvc.perform(post(ADD_TO_CART_ENDPOINT)
                            .with(JwtTestUtils.jwtUser(KEYCLOAK_ID))
                            .with(csrf())
                            .accept(APPLICATION_JSON)
                            .contentType(APPLICATION_JSON)
                            .content(asJsonString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(APPLICATION_JSON))
                    .andExpect(jsonPath("$.message", startsWith("Validation failed:")))
                    .andExpect(jsonPath("$.httpStatusCode").value(400))
                    .andExpect(jsonPath("$.errorCodeType").value("TECHNICAL"));
        }

        @Test
        void failAddToCart_whenNotEnoughStock_thenConflict() throws Exception {
            // BUG-008 fixed in Wave F2: handler now maps NotEnoughStockException -> 409 CONFLICT FUNCTIONAL.
            CartCreateRequestDto request = CartDtoFixtures.aValidCartCreateRequest();
            String message = "Not enough stock for product XYZ";

            ErrorResponseDto errorResponseDto = ErrorResponseDto.builder()
                    .message(message)
                    .httpStatusCode(409)
                    .errorCodeType(FUNCTIONAL)
                    .build();

            when(cartService.addItemsToCart(any(CartCreateRequestDto.class), anyString()))
                    .thenThrow(new NotEnoughStockException(message));

            mockMvc.perform(post(ADD_TO_CART_ENDPOINT)
                            .with(JwtTestUtils.jwtUser(KEYCLOAK_ID))
                            .with(csrf())
                            .accept(APPLICATION_JSON)
                            .contentType(APPLICATION_JSON)
                            .content(asJsonString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(content().contentType(APPLICATION_JSON))
                    .andExpect(content().json(asJsonString(errorResponseDto), STRICT));
        }

        @Test
        void shouldRemoveFromCartSuccessfully() throws Exception {
            UUID productUuid = UUID.randomUUID();
            CartResponseDto response = CartDtoFixtures.aSampleCartResponse();

            when(cartService.removeItemFromCart(eq(productUuid), anyString())).thenReturn(response);

            mockMvc.perform(patch(REMOVE_FROM_CART_ENDPOINT, productUuid)
                            .with(JwtTestUtils.jwtUser(KEYCLOAK_ID))
                            .with(csrf())
                            .accept(APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(APPLICATION_JSON))
                    .andExpect(content().json(asJsonString(response), STRICT));
        }

        @Test
        void shouldRemoveFromCart_forwardsJwtSubjectAndProductUuid() throws Exception {
            UUID productUuid = UUID.randomUUID();
            CartResponseDto response = CartDtoFixtures.aSampleCartResponse();
            when(cartService.removeItemFromCart(eq(productUuid), anyString())).thenReturn(response);

            mockMvc.perform(patch(REMOVE_FROM_CART_ENDPOINT, productUuid)
                            .with(JwtTestUtils.jwtUser(KEYCLOAK_ID))
                            .with(csrf())
                            .accept(APPLICATION_JSON))
                    .andExpect(status().isOk());

            ArgumentCaptor<UUID> uuidCaptor = ArgumentCaptor.forClass(UUID.class);
            ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
            verify(cartService).removeItemFromCart(uuidCaptor.capture(), subjectCaptor.capture());
            assertThat(uuidCaptor.getValue()).isEqualTo(productUuid);
            assertThat(subjectCaptor.getValue()).isEqualTo(KEYCLOAK_ID);
        }

        @Test
        void failRemoveFromCart_whenInvalidUuidPath_thenBadRequest() throws Exception {
            // BUG-029 fixed in Wave F2: MethodArgumentTypeMismatchException handler now returns 400.
            ErrorResponseDto errorResponseDto = ErrorResponseDto.builder()
                    .message("Invalid value for parameter 'productUuid'")
                    .httpStatusCode(400)
                    .errorCodeType(TECHNICAL)
                    .build();

            mockMvc.perform(patch("/api/v1/services/cart/remove/not-a-uuid")
                            .with(JwtTestUtils.jwtUser(KEYCLOAK_ID))
                            .with(csrf())
                            .accept(APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(APPLICATION_JSON))
                    .andExpect(content().json(asJsonString(errorResponseDto), STRICT));
        }

        @Test
        void shouldDecreaseQuantitySuccessfully() throws Exception {
            CartItemRemoveRequestDto request = CartDtoFixtures.aValidCartItemRemoveRequest();
            CartResponseDto response = CartDtoFixtures.aSampleCartResponse();

            when(cartService.decreaseQuantity(any(CartItemRemoveRequestDto.class), anyString())).thenReturn(response);

            mockMvc.perform(delete(DECREASE_QUANTITY_ENDPOINT)
                            .with(JwtTestUtils.jwtUser(KEYCLOAK_ID))
                            .with(csrf())
                            .accept(APPLICATION_JSON)
                            .contentType(APPLICATION_JSON)
                            .content(asJsonString(request)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(APPLICATION_JSON))
                    .andExpect(content().json(asJsonString(response), STRICT));
        }

        @Test
        void failDecreaseQuantity_whenNegativeQuantity_thenBadRequest() throws Exception {
            // CartItemRemoveRequestDto is the root @Valid body — @Min(1) fires regardless of BUG-028.
            CartItemRemoveRequestDto bad = CartItemRemoveRequestDto.builder()
                    .productUuid(UUID.randomUUID())
                    .quantity(-3)
                    .build();

            mockMvc.perform(delete(DECREASE_QUANTITY_ENDPOINT)
                            .with(JwtTestUtils.jwtUser(KEYCLOAK_ID))
                            .with(csrf())
                            .accept(APPLICATION_JSON)
                            .contentType(APPLICATION_JSON)
                            .content(asJsonString(bad)))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(APPLICATION_JSON))
                    .andExpect(jsonPath("$.message", startsWith("Validation failed:")))
                    .andExpect(jsonPath("$.httpStatusCode").value(400))
                    .andExpect(jsonPath("$.errorCodeType").value("TECHNICAL"));
        }
    }

    // -----------------------------------------------------------------
    // Authn / Authz cross-cutting
    // -----------------------------------------------------------------
    @Nested
    class CartAuthnAuthz {

        @Test
        void whenAnonymousAccessGetCart_thenUnauthorized() throws Exception {
            // BUG-030 fixed by W0: TestSecurityConfig now wires CustomAuthenticationEntryPoint -> 401.
            mockMvc.perform(get(GET_MY_CART_ENDPOINT)
                            .accept(APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void whenAnonymousAddToCart_thenUnauthorized() throws Exception {
            // BUG-030 fixed by W0: anonymous now properly returns 401, not 403.
            CartCreateRequestDto request = CartDtoFixtures.aValidCartCreateRequest();
            mockMvc.perform(post(ADD_TO_CART_ENDPOINT)
                            .with(csrf())
                            .accept(APPLICATION_JSON)
                            .contentType(APPLICATION_JSON)
                            .content(asJsonString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void whenAnonymousDeleteCartByUuid_thenUnauthorized() throws Exception {
            UUID cartUuid = UUID.randomUUID();
            mockMvc.perform(delete(DELETE_CART_ENDPOINT, cartUuid)
                            .with(csrf())
                            .accept(APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void whenAdminUserGetsCart_thenAllowed() throws Exception {
            // Admin role is also accepted (PreAuthorize allows USER or ADMIN).
            CartResponseDto response = CartDtoFixtures.aSampleCartResponse();
            when(cartService.getCart(anyString())).thenReturn(response);

            mockMvc.perform(get(GET_MY_CART_ENDPOINT)
                            .with(JwtTestUtils.jwtAdmin("adminId"))
                            .accept(APPLICATION_JSON))
                    .andExpect(status().isOk());
        }

        @Test
        void failClearCart_whenServiceThrowsCartNotFound_thenNotFound() throws Exception {
            // BUG-025 fixed: CartNotFoundException -> 404 even for the keycloakId-driven path.
            String message = "No cart found for user: " + KEYCLOAK_ID;
            ErrorResponseDto errorResponseDto = ErrorResponseDto.builder()
                    .message(message)
                    .httpStatusCode(404)
                    .errorCodeType(FUNCTIONAL)
                    .build();
            doThrow(new CartNotFoundException(message)).when(cartService).clearCart(anyString());

            mockMvc.perform(delete(CLEAR_CART_ENDPOINT)
                            .with(JwtTestUtils.jwtUser(KEYCLOAK_ID))
                            .with(csrf())
                            .accept(APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentType(APPLICATION_JSON))
                    .andExpect(content().json(asJsonString(errorResponseDto), STRICT));
        }
    }
}
