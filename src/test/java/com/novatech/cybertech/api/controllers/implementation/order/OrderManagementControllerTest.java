package com.novatech.cybertech.api.controllers.implementation.order;

import com.novatech.cybertech.api.controllers.TestSecurityConfig;
import com.novatech.cybertech.api.controllers.implementation.OrderManagementController;
import com.novatech.cybertech.api.error.model.ErrorResponseDto;
import com.novatech.cybertech.dto.request.order.OrderCancellationRequestDto;
import com.novatech.cybertech.dto.request.order.OrderPlacingRequestDto;
import com.novatech.cybertech.dto.request.order.OrderUpdateRequestDto;
import com.novatech.cybertech.dto.response.order.OrderResponseDto;
import com.novatech.cybertech.exceptions.NotEnoughStockException;
import com.novatech.cybertech.exceptions.OrderNotFoundException;
import com.novatech.cybertech.fixtures.dto.OrderDtoFixtures;
import com.novatech.cybertech.services.implementation.OrderManagementServiceImp;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static com.novatech.cybertech.api.error.enumpackage.ErrorCodeType.FUNCTIONAL;
import static com.novatech.cybertech.api.error.enumpackage.ErrorCodeType.TECHNICAL;
import static com.novatech.cybertech.fixtures.support.JwtTestUtils.jwtAdmin;
import static com.novatech.cybertech.fixtures.support.JwtTestUtils.jwtUser;
import static com.novatech.cybertech.utils.TestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @WebMvcTest slice for {@link OrderManagementController}. Verifies endpoints, JSON envelopes,
 * DTO validation, role-based access, and that the JWT subject reaches the service layer
 * (skeptical assertion: catches a class of regressions where someone might pass the wrong
 * argument from the JWT to the service).
 */
@Slf4j
@Import({TestSecurityConfig.class})
@WebMvcTest(value = OrderManagementController.class)
class OrderManagementControllerTest {

    private static final String BASE = "/api/v1/services/management/order";
    private static final String PLACE = BASE + "/place";
    private static final String PLACE_AUTO = BASE + "/place/auto";
    private static final String CANCEL = BASE + "/cancel";
    private static final String UPDATE = BASE + "/update";
    private static final String RETRY_PAYMENT = BASE + "/retry-payment/{uuid}";
    private static final String GET_BY_UUID = BASE + "/get/{uuid}";
    private static final String DELETE_BY_UUID = BASE + "/delete/{uuid}";

    private static final String KEYCLOAK_ID = "keycloak-subject-id";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    OrderManagementServiceImp orderService;

    // ---------- POST /place ----------

    @Test
    void shouldPlaceOrderSuccessfully() throws Exception {
        OrderPlacingRequestDto request = OrderDtoFixtures.aValidPlaceOrderRequest();
        OrderResponseDto response = OrderDtoFixtures.aSampleOrderResponse();

        when(orderService.placeOrder(any(OrderPlacingRequestDto.class), any(Jwt.class))).thenReturn(response);

        mockMvc.perform(post(PLACE)
                        .with(jwtUser(KEYCLOAK_ID))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .accept(APPLICATION_JSON)
                        .content(asJsonString(request)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(response), STRICT));

        // Skeptical: the controller must forward the JWT (not e.g. a String) so the service can read its subject.
        ArgumentCaptor<Jwt> jwtCaptor = ArgumentCaptor.forClass(Jwt.class);
        verify(orderService).placeOrder(any(OrderPlacingRequestDto.class), jwtCaptor.capture());
        assertThat(jwtCaptor.getValue().getSubject()).isEqualTo(KEYCLOAK_ID);
    }

    @Test
    void shouldFailPlacingOrderWhenDtoBadRequest() throws Exception {
        OrderPlacingRequestDto bad = new OrderPlacingRequestDto();
        ErrorResponseDto errorResponseDto = ErrorResponseDto.builder()
                .message("Invalid Request or Request Poorly Constructed")
                .httpStatusCode(400)
                .errorCodeType(TECHNICAL)
                .build();

        mockMvc.perform(post(PLACE)
                        .with(jwtUser(KEYCLOAK_ID))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .accept(APPLICATION_JSON)
                        .content(asJsonString(bad)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(errorResponseDto), STRICT));
    }

    @Test
    void shouldFailPlacingOrderWhenNotEnoughStock() throws Exception {
        OrderPlacingRequestDto request = OrderDtoFixtures.aValidPlaceOrderRequest();
        ErrorResponseDto errorResponseDto = ErrorResponseDto.builder()
                .message("Not enough stock for product X")
                .httpStatusCode(409)
                .errorCodeType(FUNCTIONAL)
                .build();

        when(orderService.placeOrder(any(OrderPlacingRequestDto.class), any(Jwt.class)))
                .thenThrow(new NotEnoughStockException("Not enough stock for product X"));

        mockMvc.perform(post(PLACE)
                        .with(jwtUser(KEYCLOAK_ID))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .accept(APPLICATION_JSON)
                        .content(asJsonString(request)))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(errorResponseDto), STRICT));
    }

    @Test
    void shouldFailPlacingOrderWhenAnonymousCauseUnauthorized() throws Exception {
        OrderPlacingRequestDto request = OrderDtoFixtures.aValidPlaceOrderRequest();

        mockMvc.perform(post(PLACE)
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .accept(APPLICATION_JSON)
                        .content(asJsonString(request)))
                .andExpect(status().isUnauthorized());
    }

    // ---------- POST /place/auto ----------

    @Test
    void shouldPlaceOrderViaAutoEndpoint() throws Exception {
        // The /place/auto debug helper uses DataGenerator.orderGenerator() server-side; we only
        // assert the wiring works and the JWT reaches the service. Tracked as tech-debt: this
        // endpoint should not be exposed in production.
        OrderResponseDto response = OrderDtoFixtures.aSampleOrderResponse();
        when(orderService.placeOrder(any(OrderPlacingRequestDto.class), any(Jwt.class))).thenReturn(response);

        mockMvc.perform(post(PLACE_AUTO)
                        .with(jwtUser(KEYCLOAK_ID))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .accept(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(response), STRICT));

        ArgumentCaptor<Jwt> jwtCaptor = ArgumentCaptor.forClass(Jwt.class);
        verify(orderService).placeOrder(any(OrderPlacingRequestDto.class), jwtCaptor.capture());
        assertThat(jwtCaptor.getValue().getSubject()).isEqualTo(KEYCLOAK_ID);
    }

    // ---------- POST /cancel ----------

    @Test
    void shouldCancelOrderSuccessfully() throws Exception {
        UUID orderUuid = UUID.randomUUID();
        OrderCancellationRequestDto req = new OrderCancellationRequestDto();
        req.setOrderUuid(orderUuid);

        OrderResponseDto response = OrderDtoFixtures.aSampleOrderResponseBuilder().uuid(orderUuid).build();

        when(orderService.cancelOrder(eq(orderUuid), any(Jwt.class))).thenReturn(response);

        mockMvc.perform(post(CANCEL)
                        .with(jwtUser(KEYCLOAK_ID))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .accept(APPLICATION_JSON)
                        .content(asJsonString(req)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(response), STRICT));

        ArgumentCaptor<Jwt> jwtCaptor = ArgumentCaptor.forClass(Jwt.class);
        verify(orderService).cancelOrder(eq(orderUuid), jwtCaptor.capture());
        assertThat(jwtCaptor.getValue().getSubject()).isEqualTo(KEYCLOAK_ID);
    }

    @Test
    void shouldFailCancellingOrderWhenOrderNotFound() throws Exception {
        UUID orderUuid = UUID.randomUUID();
        OrderCancellationRequestDto req = new OrderCancellationRequestDto();
        req.setOrderUuid(orderUuid);

        ErrorResponseDto errorResponseDto = ErrorResponseDto.builder()
                .message("Order not found")
                .httpStatusCode(404)
                .errorCodeType(FUNCTIONAL)
                .build();

        when(orderService.cancelOrder(eq(orderUuid), any(Jwt.class)))
                .thenThrow(new OrderNotFoundException("Order not found"));

        mockMvc.perform(post(CANCEL)
                        .with(jwtUser(KEYCLOAK_ID))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .accept(APPLICATION_JSON)
                        .content(asJsonString(req)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(errorResponseDto), STRICT));
    }

    @Test
    void shouldFailCancellingOrderWhenAnonymousCauseUnauthorized() throws Exception {
        OrderCancellationRequestDto req = new OrderCancellationRequestDto();
        req.setOrderUuid(UUID.randomUUID());

        mockMvc.perform(post(CANCEL)
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .accept(APPLICATION_JSON)
                        .content(asJsonString(req)))
                .andExpect(status().isUnauthorized());
    }

    // ---------- POST /update ----------

    @Test
    void shouldUpdateOrderSuccessfully() throws Exception {
        UUID orderUuid = UUID.randomUUID();
        OrderUpdateRequestDto req = OrderDtoFixtures.aValidUpdateRequestBuilder().uuid(orderUuid).build();
        OrderResponseDto response = OrderDtoFixtures.aSampleOrderResponseBuilder().uuid(orderUuid).build();

        when(orderService.updateOrder(any(OrderUpdateRequestDto.class), any(Jwt.class))).thenReturn(response);

        mockMvc.perform(post(UPDATE)
                        .with(jwtUser(KEYCLOAK_ID))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .accept(APPLICATION_JSON)
                        .content(asJsonString(req)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(response), STRICT));

        ArgumentCaptor<Jwt> jwtCaptor = ArgumentCaptor.forClass(Jwt.class);
        verify(orderService).updateOrder(any(OrderUpdateRequestDto.class), jwtCaptor.capture());
        assertThat(jwtCaptor.getValue().getSubject()).isEqualTo(KEYCLOAK_ID);
    }

    @Test
    void shouldFailUpdatingOrderWhenDtoBadRequest() throws Exception {
        OrderUpdateRequestDto bad = new OrderUpdateRequestDto();
        ErrorResponseDto errorResponseDto = ErrorResponseDto.builder()
                .message("Invalid Request or Request Poorly Constructed")
                .httpStatusCode(400)
                .errorCodeType(TECHNICAL)
                .build();

        mockMvc.perform(post(UPDATE)
                        .with(jwtUser(KEYCLOAK_ID))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .accept(APPLICATION_JSON)
                        .content(asJsonString(bad)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(errorResponseDto), STRICT));
    }

    @Test
    void shouldFailUpdatingOrderWhenOrderNotFound() throws Exception {
        OrderUpdateRequestDto req = OrderDtoFixtures.aValidUpdateRequest();
        ErrorResponseDto errorResponseDto = ErrorResponseDto.builder()
                .message("Order not found for update")
                .httpStatusCode(404)
                .errorCodeType(FUNCTIONAL)
                .build();

        when(orderService.updateOrder(any(OrderUpdateRequestDto.class), any(Jwt.class)))
                .thenThrow(new OrderNotFoundException("Order not found for update"));

        mockMvc.perform(post(UPDATE)
                        .with(jwtUser(KEYCLOAK_ID))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .accept(APPLICATION_JSON)
                        .content(asJsonString(req)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(errorResponseDto), STRICT));
    }

    // ---------- POST /retry-payment/{uuid} ----------

    @Test
    void shouldRetryPaymentSuccessfully() throws Exception {
        UUID orderUuid = UUID.randomUUID();
        OrderResponseDto response = OrderDtoFixtures.aSampleOrderResponseBuilder().uuid(orderUuid).build();

        when(orderService.retryPayment(eq(orderUuid), any(Jwt.class))).thenReturn(response);

        mockMvc.perform(post(RETRY_PAYMENT, orderUuid)
                        .with(jwtUser(KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(response), STRICT));

        ArgumentCaptor<Jwt> jwtCaptor = ArgumentCaptor.forClass(Jwt.class);
        verify(orderService).retryPayment(eq(orderUuid), jwtCaptor.capture());
        assertThat(jwtCaptor.getValue().getSubject()).isEqualTo(KEYCLOAK_ID);
    }

    @Test
    void shouldFailRetryPaymentWhenOrderNotFound() throws Exception {
        UUID orderUuid = UUID.randomUUID();
        ErrorResponseDto errorResponseDto = ErrorResponseDto.builder()
                .message("Order not found for retry")
                .httpStatusCode(404)
                .errorCodeType(FUNCTIONAL)
                .build();

        when(orderService.retryPayment(eq(orderUuid), any(Jwt.class)))
                .thenThrow(new OrderNotFoundException("Order not found for retry"));

        mockMvc.perform(post(RETRY_PAYMENT, orderUuid)
                        .with(jwtUser(KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(errorResponseDto), STRICT));
    }

    @Test
    void shouldFailRetryPaymentWhenPathUuidMalformed() throws Exception {
        // BUG-029 (per F2): path-UUID type mismatch should yield 400 via MethodArgumentTypeMismatchException handler.
        ErrorResponseDto errorResponseDto = ErrorResponseDto.builder()
                .message("Invalid value for parameter 'uuid'")
                .httpStatusCode(400)
                .errorCodeType(TECHNICAL)
                .build();

        mockMvc.perform(post(BASE + "/retry-payment/not-a-uuid")
                        .with(jwtUser(KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(errorResponseDto), STRICT));
    }

    // ---------- GET /get/{uuid} ----------

    @Test
    void shouldGetOrderByUuidSuccessfully() throws Exception {
        UUID orderUuid = UUID.randomUUID();
        OrderResponseDto response = OrderDtoFixtures.aSampleOrderResponseBuilder().uuid(orderUuid).build();
        when(orderService.getByUUID(orderUuid)).thenReturn(response);

        mockMvc.perform(get(GET_BY_UUID, orderUuid)
                        .with(jwtUser(KEYCLOAK_ID))
                        .accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(response), STRICT));
    }

    @Test
    void shouldFailGettingOrderByUuidWhenNotFound() throws Exception {
        UUID orderUuid = UUID.randomUUID();
        ErrorResponseDto errorResponseDto = ErrorResponseDto.builder()
                .message("No product with the UUID : " + orderUuid + " found")
                .httpStatusCode(404)
                .errorCodeType(FUNCTIONAL)
                .build();

        when(orderService.getByUUID(orderUuid))
                .thenThrow(new OrderNotFoundException("No product with the UUID : " + orderUuid + " found"));

        mockMvc.perform(get(GET_BY_UUID, orderUuid)
                        .with(jwtUser(KEYCLOAK_ID))
                        .accept(APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(errorResponseDto), STRICT));
    }

    @Test
    void shouldFailGettingOrderByUuidAsAdminCauseForbidden() throws Exception {
        // GET /get/{uuid} is annotated @PreAuthorize("hasRole('USER')") only. ADMIN should be rejected.
        UUID orderUuid = UUID.randomUUID();
        ErrorResponseDto errorResponseDto = ErrorResponseDto.builder()
                .message("Access denied")
                .httpStatusCode(403)
                .errorCodeType(FUNCTIONAL)
                .build();

        mockMvc.perform(get(GET_BY_UUID, orderUuid)
                        .with(jwtAdmin("admin-id"))
                        .accept(APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(errorResponseDto), STRICT));
    }

    @Test
    void shouldFailGettingOrderByUuidWhenAnonymousCauseUnauthorized() throws Exception {
        UUID orderUuid = UUID.randomUUID();

        mockMvc.perform(get(GET_BY_UUID, orderUuid)
                        .accept(APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    // ---------- DELETE /delete/{uuid} ----------

    @Test
    void shouldDeleteOrderByUuidAsAdmin() throws Exception {
        UUID orderUuid = UUID.randomUUID();
        doNothing().when(orderService).deleteByUUID(eq(orderUuid), any(Jwt.class));

        mockMvc.perform(delete(DELETE_BY_UUID, orderUuid)
                        .with(jwtAdmin("admin-id"))
                        .with(csrf())
                        .accept(APPLICATION_JSON))
                .andExpect(status().isNoContent());

        ArgumentCaptor<Jwt> jwtCaptor = ArgumentCaptor.forClass(Jwt.class);
        verify(orderService).deleteByUUID(eq(orderUuid), jwtCaptor.capture());
        assertThat(jwtCaptor.getValue().getSubject()).isEqualTo("admin-id");
    }

    @Test
    void shouldFailDeletingOrderByUuidAsUserCauseForbidden() throws Exception {
        // BUG-020 update: with W0's @EnableMethodSecurity(proxyTargetClass = true) the @PreAuthorize
        // is now enforced — ROLE_USER should be rejected with 403 via the AuthorizationDeniedException handler.
        UUID orderUuid = UUID.randomUUID();
        ErrorResponseDto errorResponseDto = ErrorResponseDto.builder()
                .message("Access denied")
                .httpStatusCode(403)
                .errorCodeType(FUNCTIONAL)
                .build();

        mockMvc.perform(delete(DELETE_BY_UUID, orderUuid)
                        .with(jwtUser(KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(errorResponseDto), STRICT));
    }

    @Test
    void shouldFailDeletingOrderByUuidWhenAnonymousCauseUnauthorized() throws Exception {
        UUID orderUuid = UUID.randomUUID();

        mockMvc.perform(delete(DELETE_BY_UUID, orderUuid)
                        .with(csrf())
                        .accept(APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldFailDeletingOrderByUuidWhenOrderNotFound() throws Exception {
        UUID orderUuid = UUID.randomUUID();
        ErrorResponseDto errorResponseDto = ErrorResponseDto.builder()
                .message("Order not found for delete")
                .httpStatusCode(404)
                .errorCodeType(FUNCTIONAL)
                .build();

        doThrow(new OrderNotFoundException("Order not found for delete"))
                .when(orderService).deleteByUUID(eq(orderUuid), any(Jwt.class));

        mockMvc.perform(delete(DELETE_BY_UUID, orderUuid)
                        .with(jwtAdmin("admin-id"))
                        .with(csrf())
                        .accept(APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(errorResponseDto), STRICT));
    }
}
