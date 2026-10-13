package com.novatech.cybertech.api.controllers.implementation.shipping;

import com.novatech.cybertech.api.controllers.TestSecurityConfig;
import com.novatech.cybertech.api.controllers.implementation.ShippingController;
import com.novatech.cybertech.dto.response.shipping.ShippingOptionResponseDto;
import com.novatech.cybertech.entities.enums.ShippingProvider;
import com.novatech.cybertech.entities.enums.ShippingType;
import com.novatech.cybertech.services.core.ShippingOptionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpHeaders.CACHE_CONTROL;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @WebMvcTest slice for {@link ShippingController}: public access, JSON contract, cache header,
 * provider filter forwarding and the 400 on an unknown provider.
 */
@Import(TestSecurityConfig.class)
@WebMvcTest(ShippingController.class)
class ShippingControllerTest {

    private static final String OPTIONS = "/api/v1/services/shipping/options";

    @Autowired MockMvc mockMvc;

    @MockitoBean ShippingOptionService shippingOptionService;

    private static ShippingOptionResponseDto option(final ShippingProvider provider, final ShippingType type, final String cost) {
        return ShippingOptionResponseDto.builder().provider(provider).type(type).cost(new BigDecimal(cost)).currency("EUR").build();
    }

    @Test
    void options_anonymous_returns200WithPublicCacheHeader() throws Exception {
        when(shippingOptionService.getShippingOptions(null)).thenReturn(List.of(
                option(ShippingProvider.DHL, ShippingType.STANDARD, "15.00"),
                option(ShippingProvider.DHL, ShippingType.EXPRESS, "25.00")));

        mockMvc.perform(get(OPTIONS).accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string(CACHE_CONTROL, containsString("public")))
                .andExpect(header().string(CACHE_CONTROL, containsString("max-age=600")))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].provider").value("DHL"))
                .andExpect(jsonPath("$[0].type").value("STANDARD"))
                .andExpect(jsonPath("$[0].cost").value(15.00))
                .andExpect(jsonPath("$[0].currency").value("EUR"))
                .andExpect(jsonPath("$[1].type").value("EXPRESS"));
    }

    @Test
    void options_providerFilter_isForwardedToTheService() throws Exception {
        when(shippingOptionService.getShippingOptions(ShippingProvider.FEDEX)).thenReturn(List.of(
                option(ShippingProvider.FEDEX, ShippingType.STANDARD, "15.00")));

        mockMvc.perform(get(OPTIONS).param("provider", "FEDEX").accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].provider").value("FEDEX"));
    }

    @Test
    void options_unknownProvider_returns400MethodArgumentTypeMismatch() throws Exception {
        mockMvc.perform(get(OPTIONS).param("provider", "UPS").accept(APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("METHOD_ARGUMENT_TYPE_MISMATCH"));

        verify(shippingOptionService, never()).getShippingOptions(any());
    }
}
