package com.novatech.cybertech.api.controllers.implementation;

import com.novatech.cybertech.api.controllers.TestSecurityConfig;
import com.novatech.cybertech.api.error.model.ErrorResponseDto;
import com.novatech.cybertech.dto.request.order.OrderCancellationRequestDto;
import com.novatech.cybertech.dto.request.order.OrderPlacingRequestDto;
import com.novatech.cybertech.dto.request.order.OrderUpdateRequestDto;
import com.novatech.cybertech.dto.request.orderItem.OrderItemCreateRequestDto;
import com.novatech.cybertech.dto.response.order.OrderItemResponseDto;
import com.novatech.cybertech.dto.response.order.OrderResponseDto;
import com.novatech.cybertech.entities.enums.PaymentType;
import com.novatech.cybertech.entities.enums.ShippingProvider;
import com.novatech.cybertech.entities.enums.ShippingType;
import com.novatech.cybertech.services.implementation.OrderManagementServiceImp;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.novatech.cybertech.api.error.enumpackage.ErrorCodeType.TECHNICAL;
import static com.novatech.cybertech.utils.TestUtils.asJsonString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.json.JsonCompareMode.STRICT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Slf4j
@Import({TestSecurityConfig.class})
@WebMvcTest(value = com.novatech.cybertech.api.controllers.implementation.OrderManagementController.class)
class OrderManagementControllerTest {

    private static final String BASE = "/api/v1/services/management/order";
    private static final String PLACE = BASE + "/place";
    private static final String CANCEL = BASE + "/cancel";
    private static final String UPDATE = BASE + "/update";
    private static final String RETRY_PAYMENT = BASE + "/retry-payment/{uuid}";
    private static final String GET = BASE + "/get/{uuid}";
    private static final String DELETE = BASE + "/delete/{uuid}";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    OrderManagementServiceImp orderService;


    
    @Test
    void shouldPlaceOrderSuccessfully() throws Exception {
        UUID userUuid = UUID.randomUUID();
        OrderItemCreateRequestDto item = OrderItemCreateRequestDto.builder().productUuid(UUID.randomUUID()).quantity(2).build();
        OrderPlacingRequestDto request = OrderPlacingRequestDto.builder()
                .userUuid(userUuid)
                .shippingType(ShippingType.STANDARD)
                .shippingProvider(ShippingProvider.FEDEX)
                .paymentType(PaymentType.MASTERCARD)
                .build();

        OrderItemResponseDto itemResp = OrderItemResponseDto.builder().orderItemUuid(UUID.randomUUID()).productUuid(item.getProductUuid()).productName("prod").quantity(2).unitPrice(new BigDecimal("10.00")).lineItemTotalPrice(new BigDecimal("20.00")).build();
        OrderResponseDto response = OrderResponseDto.builder()
                .uuid(UUID.randomUUID())
                .userUuid(userUuid)
                .orderDate(LocalDate.now())
                .shippingAddress("123 Test St")
                .totalAmount(new BigDecimal("20.00"))
                .orderItems(List.of(itemResp))
                .build();

        when(orderService.placeOrder(any(OrderPlacingRequestDto.class), any())).thenReturn(response);

        mockMvc.perform(post(PLACE)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")).jwt(jwt -> jwt.subject("keycloakId")))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .accept(APPLICATION_JSON)
                        .content(asJsonString(request)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(response), STRICT));
    }

    @Test
    void shouldFailPlacingOrderBadRequest() throws Exception {
        OrderPlacingRequestDto bad = new OrderPlacingRequestDto();
        ErrorResponseDto errorResponseDto = ErrorResponseDto.builder().message("Invalid Request or Request Poorly Constructed").httpStatusCode(400).errorCodeType(TECHNICAL).build();

        mockMvc.perform(post(PLACE)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")).jwt(jwt -> jwt.subject("keycloakId")))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .accept(APPLICATION_JSON)
                        .content(asJsonString(bad)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(errorResponseDto), STRICT));
    }

    @Test
    void shouldCancelOrderSuccessfully() throws Exception {
        UUID orderUuid = UUID.randomUUID();
        OrderCancellationRequestDto req = new OrderCancellationRequestDto();
        req.setOrderUuid(orderUuid);

        OrderResponseDto response = OrderResponseDto.builder().uuid(orderUuid).status(null).build();

        when(orderService.cancelOrder(eq(orderUuid), any())).thenReturn(response);

        mockMvc.perform(post(CANCEL)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")).jwt(jwt -> jwt.subject("keycloakId")))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .accept(APPLICATION_JSON)
                        .content(asJsonString(req)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(response), STRICT));
    }

    @Test
    void shouldGetOrderByUuidSuccessfully() throws Exception {
        UUID orderUuid = UUID.randomUUID();
        OrderResponseDto response = OrderResponseDto.builder().uuid(orderUuid).build();
        when(orderService.getByUUID(orderUuid)).thenReturn(response);

        mockMvc.perform(get(GET, orderUuid)
                        .contentType(APPLICATION_JSON)
                        .accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(response), STRICT));
    }

    @Test
    void shouldRetryPaymentSuccessfully() throws Exception {
        UUID orderUuid = UUID.randomUUID();
        OrderResponseDto response = OrderResponseDto.builder().uuid(orderUuid).build();
        when(orderService.retryPayment(orderUuid, any())).thenReturn(response);

        mockMvc.perform(post(RETRY_PAYMENT, orderUuid)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")).jwt(jwt -> jwt.subject("keycloakId")))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(response), STRICT));
    }

    @Test
    void shouldDeleteOrderByUuidAsAdmin() throws Exception {
        UUID orderUuid = UUID.randomUUID();
        doNothing().when(orderService).deleteByUUID(orderUuid, any());

        mockMvc.perform(delete(DELETE, orderUuid)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")).jwt(jwt -> jwt.subject("admin")))
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldUpdateOrderSuccessfully() throws Exception {
        UUID orderUuid = UUID.randomUUID();
        OrderItemCreateRequestDto item = OrderItemCreateRequestDto.builder().productUuid(UUID.randomUUID()).quantity(1).build();
        OrderUpdateRequestDto req = OrderUpdateRequestDto.builder()
                .uuid(orderUuid)
                .paymentType(PaymentType.MASTERCARD)
                .shippingProvider(ShippingProvider.FEDEX)
                .shippingType(ShippingType.EXPRESS)
                .itemUpdateRequestDtoList(List.of(item))
                .build();

        OrderResponseDto response = OrderResponseDto.builder().uuid(orderUuid).build();
        when(orderService.updateOrder(any(OrderUpdateRequestDto.class), any())).thenReturn(response);

        mockMvc.perform(post(UPDATE)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")).jwt(jwt -> jwt.subject("keycloakId")))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .accept(APPLICATION_JSON)
                        .content(asJsonString(req)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(response), STRICT));
    }

}

