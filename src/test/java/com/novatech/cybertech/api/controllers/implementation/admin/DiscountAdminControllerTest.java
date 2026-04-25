package com.novatech.cybertech.api.controllers.implementation.admin;

import com.novatech.cybertech.api.controllers.TestSecurityConfig;
import com.novatech.cybertech.api.controllers.implementation.DiscountAdminController;
import com.novatech.cybertech.dto.request.admin.DiscountCampaignUpdateRequestDto;
import com.novatech.cybertech.dto.response.admin.DiscountCampaignResponseDto;
import com.novatech.cybertech.entities.enums.DiscountCalculationType;
import com.novatech.cybertech.entities.enums.DiscountType;
import com.novatech.cybertech.exceptions.DiscountTypeNotActiveException;
import com.novatech.cybertech.services.core.DiscountCampaignAdminService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static com.novatech.cybertech.fixtures.support.JwtTestUtils.jwtAdmin;
import static com.novatech.cybertech.fixtures.support.JwtTestUtils.jwtUser;
import static com.novatech.cybertech.utils.TestUtils.asJsonString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestSecurityConfig.class)
@WebMvcTest(DiscountAdminController.class)
class DiscountAdminControllerTest {

    private static final String BASE = "/api/v1/services/admin/discounts";
    private static final String KC = "kc-admin";

    @Autowired MockMvc mockMvc;

    @MockitoBean DiscountCampaignAdminService discountCampaignAdminService;

    private DiscountCampaignResponseDto sample(final DiscountType type, final boolean enabled) {
        return DiscountCampaignResponseDto.builder()
                .uuid(UUID.randomUUID())
                .discountType(type)
                .calculationType(DiscountCalculationType.PERCENTAGE)
                .enabled(enabled)
                .percentage(new BigDecimal("20.00"))
                .priority(50)
                .build();
    }

    @Test
    void getAll_admin_ok() throws Exception {
        when(discountCampaignAdminService.getAll()).thenReturn(List.of(
                sample(DiscountType.BLACK_FRIDAY, true),
                sample(DiscountType.WINTER_SALES, false)));

        mockMvc.perform(get(BASE).with(jwtAdmin(KC)).accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$[0].discountType").value("BLACK_FRIDAY"))
                .andExpect(jsonPath("$[1].discountType").value("WINTER_SALES"));
    }

    @Test
    void getAll_nonAdmin_forbidden() throws Exception {
        mockMvc.perform(get(BASE).with(jwtUser(KC)).accept(APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void getAll_anonymous_unauthorized() throws Exception {
        mockMvc.perform(get(BASE).accept(APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getByDiscountType_admin_ok() throws Exception {
        when(discountCampaignAdminService.getByDiscountType(DiscountType.BLACK_FRIDAY))
                .thenReturn(sample(DiscountType.BLACK_FRIDAY, true));

        mockMvc.perform(get(BASE + "/{discountType}", DiscountType.BLACK_FRIDAY)
                        .with(jwtAdmin(KC)).accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discountType").value("BLACK_FRIDAY"))
                .andExpect(jsonPath("$.percentage").value(20.00));
    }

    @Test
    void getByDiscountType_missing_returns400() throws Exception {
        when(discountCampaignAdminService.getByDiscountType(DiscountType.BLACK_FRIDAY))
                .thenThrow(new DiscountTypeNotActiveException("Discount campaign for BLACK_FRIDAY does not exist"));

        mockMvc.perform(get(BASE + "/{discountType}", DiscountType.BLACK_FRIDAY)
                        .with(jwtAdmin(KC)).accept(APPLICATION_JSON))
                // ErrorManagementController maps DiscountTypeNotActiveException to 400 FUNCTIONAL via DISCOUNT_TYPE_NOT_ACTIVE
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCodeType").value("FUNCTIONAL"));
    }

    @Test
    void update_admin_ok_evictsCacheAndReturnsUpdated() throws Exception {
        final DiscountCampaignUpdateRequestDto patch = new DiscountCampaignUpdateRequestDto(
                true, null, new BigDecimal("35.00"), null, null, null, null, null, null);
        when(discountCampaignAdminService.update(eq(DiscountType.BLACK_FRIDAY), any(DiscountCampaignUpdateRequestDto.class)))
                .thenReturn(DiscountCampaignResponseDto.builder()
                        .uuid(UUID.randomUUID())
                        .discountType(DiscountType.BLACK_FRIDAY)
                        .calculationType(DiscountCalculationType.PERCENTAGE)
                        .enabled(true)
                        .percentage(new BigDecimal("35.00"))
                        .build());

        mockMvc.perform(patch(BASE + "/{discountType}", DiscountType.BLACK_FRIDAY)
                        .with(jwtAdmin(KC))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .accept(APPLICATION_JSON)
                        .content(asJsonString(patch)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.percentage").value(35.00));

        verify(discountCampaignAdminService).update(eq(DiscountType.BLACK_FRIDAY), any(DiscountCampaignUpdateRequestDto.class));
    }

    @Test
    void update_nonAdmin_forbidden() throws Exception {
        final DiscountCampaignUpdateRequestDto patch = new DiscountCampaignUpdateRequestDto(
                true, null, null, null, null, null, null, null, null);

        mockMvc.perform(patch(BASE + "/{discountType}", DiscountType.BLACK_FRIDAY)
                        .with(jwtUser(KC))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .accept(APPLICATION_JSON)
                        .content(asJsonString(patch)))
                .andExpect(status().isForbidden());
    }

    @Test
    void update_invalidPercentage_400() throws Exception {
        // percentage > 100 should fail @DecimalMax validation
        final DiscountCampaignUpdateRequestDto patch = new DiscountCampaignUpdateRequestDto(
                null, null, new BigDecimal("150.00"), null, null, null, null, null, null);

        mockMvc.perform(patch(BASE + "/{discountType}", DiscountType.BLACK_FRIDAY)
                        .with(jwtAdmin(KC))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .accept(APPLICATION_JSON)
                        .content(asJsonString(patch)))
                .andExpect(status().isBadRequest());
    }
}
