package com.novatech.cybertech.api.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novatech.cybertech.config.SecurityConfig;
import com.novatech.cybertech.dto.request.order.OrderCancellationRequestDto;
import com.novatech.cybertech.dto.request.order.OrderPlacingRequestDto;
import com.novatech.cybertech.dto.response.order.OrderItemResponseDto;
import com.novatech.cybertech.dto.response.order.OrderResponseDto;
import com.novatech.cybertech.entities.enums.OrderStatus;
import com.novatech.cybertech.entities.enums.PaymentType;
import com.novatech.cybertech.entities.enums.ShippingProvider;
import com.novatech.cybertech.entities.enums.ShippingType;
import com.novatech.cybertech.services.implementation.OrderManagementServiceImp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.novatech.cybertech.constants.CyberTechAppConstants.ORDER_MANAGEMENT_CONTROLLER_BASE_PATH;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

//@AutoConfigureMockMvc
@Import(SecurityConfig.class)
@ExtendWith(MockitoExtension.class)
//@WebMvcTest(OrderManagementController.class)
class OrderManagementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OrderManagementServiceImp orderManagementService;

    /*
    @Test
    void placeOrder_shouldReturnCreated_whenUserIsAuthorized() throws Exception {
        // Given
        final UUID userUuid = UUID.randomUUID();
        final UUID productUuid = UUID.randomUUID();

        OrderPlacingRequestDto requestDto = OrderPlacingRequestDto.builder()
                .paymentType(PaymentType.VISA)
                .userUuid(userUuid)
                .shippingStreet("123 Random Street")
                .shippingCity("Random City")
                .shippingZipCode("12345")
                .shippingCountry("Randomland")
                //.idempotencyKey(UUID.randomUUID().toString())
                .shippingType(ShippingType.EXPRESS)
                .shippingProvider(ShippingProvider.FEDEX)
                .build();

        // TODO: Remplissez les champs obligatoires de requestDto ici pour passer la validation @Valid
        // ex: requestDto.setProducts(List.of(...));

        String expectedShippingAddress = "123 Random Street, 12345 Random City, Randomland";
        OrderResponseDto responseDto = OrderResponseDto.builder()
                .userUuid(userUuid)
                .orderDate(LocalDate.of(2026, 1, 10))
                .status(OrderStatus.SHIPPED)
                .totalAmount(BigDecimal.TEN)
                .shippingAddress(expectedShippingAddress)
                .orderItems(List.of(OrderItemResponseDto.builder()
                        .orderItemUuid(UUID.randomUUID())
                        .productUuid(productUuid)
                        .productName("AZUS")
                        .quantity(5)
                        .unitPrice(BigDecimal.valueOf(1000L))
                        .lineItemTotalPrice(BigDecimal.valueOf(5000L))
                        .build()))
                .build();

        when(orderManagementService.placeOrder(any(OrderPlacingRequestDto.class), any())).thenReturn(responseDto);

        // When & Then
        mockMvc.perform(post(ORDER_MANAGEMENT_CONTROLLER_BASE_PATH + "/place")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))) // Simule un JWT avec ROLE_USER
                        .with(csrf()) // Nécessaire pour les méthodes POST/PUT/DELETE dans les tests WebMvc
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isCreated())
                .andExpect(content().json(objectMapper.writeValueAsString(responseDto), true));
    }

    @Test
    void cancelOrder_shouldReturnOk_whenUserIsAuthorized() throws Exception {
        // Given
        UUID orderUuid = UUID.randomUUID();
        OrderCancellationRequestDto requestDto = new OrderCancellationRequestDto();
        requestDto.setOrderUuid(orderUuid);

        OrderResponseDto responseDto = new OrderResponseDto();

        when(orderManagementService.cancelOrder(eq(orderUuid), any())).thenReturn(responseDto);

        // When & Then
        mockMvc.perform(post(ORDER_MANAGEMENT_CONTROLLER_BASE_PATH + "/cancel")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isOk());
    }

    @Test
    void getOrderByUuid_shouldReturnOk() throws Exception {
        // Given
        UUID orderUuid = UUID.randomUUID();
        OrderResponseDto responseDto = new OrderResponseDto();

        when(orderManagementService.getByUUID(orderUuid)).thenReturn(responseDto);

        // When & Then
        mockMvc.perform(get(ORDER_MANAGEMENT_CONTROLLER_BASE_PATH + "/get/{uuid}", orderUuid)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk());
    }

    @Test
    void deleteOrderByUuid_shouldReturnNoContent_whenAdmin() throws Exception {
        // Given
        UUID orderUuid = UUID.randomUUID();
        doNothing().when(orderManagementService).deleteByUUID(eq(orderUuid), any());

        // When & Then
        mockMvc.perform(delete(ORDER_MANAGEMENT_CONTROLLER_BASE_PATH + "/delete/{uuid}", orderUuid)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))) // ADMIN requis
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteOrderByUuid_shouldReturnForbidden_whenUser() throws Exception {
        // Given
        UUID orderUuid = UUID.randomUUID();

        // When & Then
        mockMvc.perform(delete(ORDER_MANAGEMENT_CONTROLLER_BASE_PATH + "/delete/{uuid}", orderUuid)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

     */
}