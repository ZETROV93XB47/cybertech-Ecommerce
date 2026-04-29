package com.novatech.cybertech.api.controllers.implementation.order;

import com.novatech.cybertech.api.controllers.TestSecurityConfig;
import com.novatech.cybertech.api.controllers.implementation.OrderManagementAdminController;
import com.novatech.cybertech.api.error.ErrorManagementController;
import com.novatech.cybertech.dto.response.order.OrderResponseDto;
import com.novatech.cybertech.entities.enums.OrderStatus;
import com.novatech.cybertech.fixtures.dto.OrderDtoFixtures;
import com.novatech.cybertech.services.core.OrderManagementService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.novatech.cybertech.fixtures.support.JwtTestUtils.jwtAdmin;
import static com.novatech.cybertech.fixtures.support.JwtTestUtils.jwtUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @WebMvcTest slice for {@link OrderManagementAdminController} — the new admin-side paginated
 * order listing introduced as Frontend-gap #2 in commit f7f0c1c.
 *
 * <p>The controller is class-annotated {@code @PreAuthorize("hasRole('ADMIN')")}; W0's
 * {@code @EnableMethodSecurity} on {@link TestSecurityConfig} enforces that on every endpoint.
 * The spec tests cover: the admin happy path, the optional {@code status} / {@code userKeycloakId}
 * query-param forwarding to the service, and security boundaries (anonymous → 401, ROLE_USER → 403).</p>
 */
@Slf4j
@Import({TestSecurityConfig.class, ErrorManagementController.class})
@WebMvcTest(value = OrderManagementAdminController.class)
class OrderManagementAdminControllerTest {

    private static final String GET_ALL_ENDPOINT = "/api/v1/services/admin/management/order/get/all";
    private static final String ADMIN_KEYCLOAK_ID = "keycloak-admin";
    private static final String USER_KEYCLOAK_ID = "keycloak-user";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    OrderManagementService orderManagementService;

    @Test
    void getAllAsAdminShouldReturn200() throws Exception {
        // Happy path: ADMIN reads the full paginated listing without any filter.
        UUID orderUuid = UUID.randomUUID();
        OrderResponseDto resp = OrderDtoFixtures.aSampleOrderResponseBuilder().uuid(orderUuid).build();
        Page<OrderResponseDto> page = new PageImpl<>(List.of(resp));

        when(orderManagementService.findAllPaged(any(), any(), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get(GET_ALL_ENDPOINT)
                        .with(jwtAdmin(ADMIN_KEYCLOAK_ID))
                        .accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].uuid").value(orderUuid.toString()))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getAllAsUserShouldReturn403() throws Exception {
        // BUG-031 / class-level @PreAuthorize: ROLE_USER hitting an admin-only endpoint must yield 403.
        mockMvc.perform(get(GET_ALL_ENDPOINT)
                        .with(jwtUser(USER_KEYCLOAK_ID))
                        .accept(APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void getAllAnonymousShouldReturn401() throws Exception {
        // Anonymous = 401 via CustomAuthenticationEntryPoint (the path is not in PUBLIC_URLS).
        mockMvc.perform(get(GET_ALL_ENDPOINT)
                        .accept(APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getAllShouldFilterByStatus() throws Exception {
        // Repeated ?status=PAID&status=CANCELED is bound to a Set<OrderStatus> and forwarded as-is.
        Page<OrderResponseDto> page = new PageImpl<>(List.of(OrderDtoFixtures.aSampleOrderResponse()));
        when(orderManagementService.findAllPaged(any(), any(), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get(GET_ALL_ENDPOINT)
                        .param("status", "PAID", "CANCELED")
                        .with(jwtAdmin(ADMIN_KEYCLOAK_ID))
                        .accept(APPLICATION_JSON))
                .andExpect(status().isOk());

        ArgumentCaptor<Set<OrderStatus>> statusCaptor = ArgumentCaptor.forClass(Set.class);
        ArgumentCaptor<String> userKeycloakIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(orderManagementService).findAllPaged(statusCaptor.capture(), userKeycloakIdCaptor.capture(), any(Pageable.class));
        assertThat(statusCaptor.getValue()).containsExactlyInAnyOrder(OrderStatus.PAID, OrderStatus.CANCELED);
        // No userKeycloakId param supplied → the controller must forward null to the service.
        assertThat(userKeycloakIdCaptor.getValue()).isNull();
    }

    @Test
    void getAllShouldFilterByUserKeycloakId() throws Exception {
        // ?userKeycloakId=kc-target is forwarded verbatim to the service so the JPQL :userKeycloakId param matches.
        Page<OrderResponseDto> page = new PageImpl<>(List.of(OrderDtoFixtures.aSampleOrderResponse()));
        when(orderManagementService.findAllPaged(any(), eq("kc-target"), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get(GET_ALL_ENDPOINT)
                        .param("userKeycloakId", "kc-target")
                        .with(jwtAdmin(ADMIN_KEYCLOAK_ID))
                        .accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));

        ArgumentCaptor<String> userKeycloakIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(orderManagementService).findAllPaged(any(), userKeycloakIdCaptor.capture(), any(Pageable.class));
        assertThat(userKeycloakIdCaptor.getValue()).isEqualTo("kc-target");
    }
}
