package com.novatech.cybertech.api.controllers.implementation.discount;

import com.novatech.cybertech.api.controllers.TestSecurityConfig;
import com.novatech.cybertech.api.controllers.implementation.DiscountController;
import com.novatech.cybertech.dto.data.DiscountContext;
import com.novatech.cybertech.entities.enums.DiscountCalculationType;
import com.novatech.cybertech.entities.enums.DiscountType;
import com.novatech.cybertech.services.core.DiscountCampaignService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static com.novatech.cybertech.fixtures.support.JwtTestUtils.jwtUser;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestSecurityConfig.class)
@WebMvcTest(DiscountController.class)
class DiscountControllerTest {

    private static final String BASE = "/api/v1/services/discounts";

    @Autowired MockMvc mockMvc;

    @MockitoBean DiscountCampaignService discountCampaignService;

    private DiscountContext sample(final DiscountType type, final BigDecimal percentage) {
        return DiscountContext.builder()
                .discountType(type)
                .calculationType(DiscountCalculationType.PERCENTAGE)
                .percentage(percentage)
                .build();
    }

    @Test
    void active_anonymous_returns200() throws Exception {
        when(discountCampaignService.getAllActiveCampaigns()).thenReturn(List.of(
                sample(DiscountType.BLACK_FRIDAY, new BigDecimal("20")),
                sample(DiscountType.WINTER_SALES, new BigDecimal("15"))));

        mockMvc.perform(get(BASE + "/active").accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$[0].discountType").value("BLACK_FRIDAY"))
                .andExpect(jsonPath("$[0].percentage").value(20))
                .andExpect(jsonPath("$[1].discountType").value("WINTER_SALES"));
    }

    @Test
    void active_authenticatedUser_returns200() throws Exception {
        when(discountCampaignService.getAllActiveCampaigns()).thenReturn(List.of(
                sample(DiscountType.BLACK_FRIDAY, new BigDecimal("20"))));

        mockMvc.perform(get(BASE + "/active").with(jwtUser("kc-1")).accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].discountType").value("BLACK_FRIDAY"));
    }

    @Test
    void active_emptyList_returns200WithEmptyArray() throws Exception {
        when(discountCampaignService.getAllActiveCampaigns()).thenReturn(List.of());

        mockMvc.perform(get(BASE + "/active").accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));
    }
}
