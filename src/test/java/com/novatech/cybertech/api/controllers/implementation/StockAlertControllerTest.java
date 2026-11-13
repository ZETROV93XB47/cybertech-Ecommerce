package com.novatech.cybertech.api.controllers.implementation;

import com.novatech.cybertech.api.controllers.TestSecurityConfig;
import com.novatech.cybertech.dto.response.stockalert.StockAlertResponseDto;
import com.novatech.cybertech.entities.enums.StockAlertStatus;
import com.novatech.cybertech.exceptions.ProductAlreadyInStockException;
import com.novatech.cybertech.exceptions.StockAlertAlreadyExistsException;
import com.novatech.cybertech.fixtures.support.JwtTestUtils;
import com.novatech.cybertech.services.core.StockAlertService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Unit tests for {@link StockAlertController} (same shape as {@code RecommendationControllerTest}). */
@Import({TestSecurityConfig.class})
@WebMvcTest(value = StockAlertController.class)
class StockAlertControllerTest {

    private static final String BASE = "/api/v1/services/stock-alerts/";
    private static final String MINE = BASE + "mine";
    private static final String KEYCLOAK_ID = "kc-alert";
    private static final UUID PRODUCT_UUID = UUID.fromString("0190a1b2-c3d4-7e5f-8a9b-0c1d2e3f4a5b");

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    StockAlertService stockAlertService;

    @Test
    void subscribe_returns201WithTheAlert() throws Exception {
        final UUID alertUuid = UUID.randomUUID();
        when(stockAlertService.subscribe(KEYCLOAK_ID, PRODUCT_UUID))
                .thenReturn(StockAlertResponseDto.builder().uuid(alertUuid).status(StockAlertStatus.PENDING).build());

        mockMvc.perform(post(BASE + PRODUCT_UUID).with(JwtTestUtils.jwtUser(KEYCLOAK_ID)).accept(APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uuid").value(alertUuid.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void subscribe_productInStock_returns409() throws Exception {
        when(stockAlertService.subscribe(KEYCLOAK_ID, PRODUCT_UUID)).thenThrow(new ProductAlreadyInStockException("in stock"));

        mockMvc.perform(post(BASE + PRODUCT_UUID).with(JwtTestUtils.jwtUser(KEYCLOAK_ID)).accept(APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PRODUCT_ALREADY_IN_STOCK"));
    }

    @Test
    void subscribe_alreadyPending_returns409() throws Exception {
        when(stockAlertService.subscribe(KEYCLOAK_ID, PRODUCT_UUID)).thenThrow(new StockAlertAlreadyExistsException("pending"));

        mockMvc.perform(post(BASE + PRODUCT_UUID).with(JwtTestUtils.jwtUser(KEYCLOAK_ID)).accept(APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("STOCK_ALERT_ALREADY_EXISTS"));
    }

    @Test
    void subscribe_anonymous_returns401() throws Exception {
        mockMvc.perform(post(BASE + PRODUCT_UUID).accept(APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(stockAlertService);
    }

    @Test
    void unsubscribe_returns204() throws Exception {
        mockMvc.perform(delete(BASE + PRODUCT_UUID).with(JwtTestUtils.jwtUser(KEYCLOAK_ID)))
                .andExpect(status().isNoContent());

        verify(stockAlertService).unsubscribe(KEYCLOAK_ID, PRODUCT_UUID);
    }

    @Test
    void getMine_defaultsToPage0Size20SortedByRequestedAtDesc() throws Exception {
        when(stockAlertService.findMine(eq(KEYCLOAK_ID), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get(MINE).with(JwtTestUtils.jwtUser(KEYCLOAK_ID)).accept(APPLICATION_JSON))
                .andExpect(status().isOk());

        final ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(stockAlertService).findMine(eq(KEYCLOAK_ID), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
        assertThat(pageable.getValue().getSort().getOrderFor("requestedAt").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void getMine_anonymous_returns401() throws Exception {
        mockMvc.perform(get(MINE).accept(APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }
}
