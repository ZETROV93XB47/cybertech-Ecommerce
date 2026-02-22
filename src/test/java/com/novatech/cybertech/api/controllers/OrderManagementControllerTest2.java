package com.novatech.cybertech.api.controllers;

import com.novatech.cybertech.api.controllers.implementation.OrderManagementController;
import com.novatech.cybertech.config.SecurityConfig;
import com.novatech.cybertech.services.implementation.OrderManagementServiceImp;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.novatech.cybertech.constants.CyberTechAppConstants.ORDER_MANAGEMENT_CONTROLLER_BASE_PATH;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

//@AutoConfigureMockMvc() // important : active les filtres security
@Import(SecurityConfig.class) // important : charge ta conf Security + converter
//@WebMvcTest(controllers = OrderManagementController.class)
class OrderManagementControllerSecurityTest2 {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    private OrderManagementServiceImp orderManagementService; // car @WebMvcTest ne charge pas les @Service

    @Test
    void deleteOrder_shouldReturn204_whenAdminRole() throws Exception {
        UUID uuid = UUID.randomUUID();

        mockMvc.perform(delete(ORDER_MANAGEMENT_CONTROLLER_BASE_PATH + "/delete/{uuid}", uuid)
                        .with(jwt().jwt(jwt -> jwt
                                .claim("sub", "admin-123")
                                .claim("realm_access", Map.of("roles", List.of("ADMIN")))
                        )))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteOrder_shouldReturn403_whenUserRole() throws Exception {
        UUID uuid = UUID.randomUUID();

        mockMvc.perform(delete(ORDER_MANAGEMENT_CONTROLLER_BASE_PATH + "/delete/{uuid}", uuid)
                        .with(jwt().jwt(jwt -> jwt
                                .claim("sub", "user-123")
                                .claim("realm_access", Map.of("roles", List.of("USER")))
                        )))
                .andExpect(status().isForbidden());
    }

}