package com.novatech.cybertech.api.controllers.implementation.admin;

import com.novatech.cybertech.api.controllers.TestSecurityConfig;
import com.novatech.cybertech.api.controllers.implementation.DiscountAdminController;
import com.novatech.cybertech.dto.request.admin.DiscountCampaignCreateRequestDto;
import com.novatech.cybertech.dto.request.admin.DiscountCampaignUpdateRequestDto;
import com.novatech.cybertech.dto.response.admin.DiscountCampaignResponseDto;
import com.novatech.cybertech.entities.enums.DiscountCalculationType;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestSecurityConfig.class)
@WebMvcTest(DiscountAdminController.class)
class DiscountAdminControllerTest {

    private static final String BASE = "/api/v1/services/admin/discounts";
    private static final String KC = "kc-admin";

    @Autowired MockMvc mockMvc;

    @MockitoBean DiscountCampaignAdminService discountCampaignAdminService;

    private DiscountCampaignResponseDto sample(final String discountKey, final boolean enabled) {
        return DiscountCampaignResponseDto.builder()
                .uuid(UUID.randomUUID())
                .discountKey(discountKey)
                .calculationType(DiscountCalculationType.PERCENTAGE)
                .enabled(enabled)
                .percentage(new BigDecimal("20.00"))
                .priority(50)
                .build();
    }

    @Test
    void getAll_admin_ok() throws Exception {
        when(discountCampaignAdminService.getAll()).thenReturn(List.of(
                sample("BLACK_FRIDAY", true),
                sample("WINTER_SALES", false)));

        mockMvc.perform(get(BASE).with(jwtAdmin(KC)).accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$[0].discountKey").value("BLACK_FRIDAY"))
                .andExpect(jsonPath("$[1].discountKey").value("WINTER_SALES"));
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
    void getByDiscountKey_admin_ok() throws Exception {
        when(discountCampaignAdminService.getByDiscountKey("BLACK_FRIDAY"))
                .thenReturn(sample("BLACK_FRIDAY", true));

        mockMvc.perform(get(BASE + "/{discountKey}", "BLACK_FRIDAY")
                        .with(jwtAdmin(KC)).accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discountKey").value("BLACK_FRIDAY"))
                .andExpect(jsonPath("$.percentage").value(20.00));
    }

    @Test
    void getByDiscountKey_missing_returns400() throws Exception {
        when(discountCampaignAdminService.getByDiscountKey("BLACK_FRIDAY"))
                .thenThrow(new DiscountTypeNotActiveException("Discount campaign for BLACK_FRIDAY does not exist"));

        mockMvc.perform(get(BASE + "/{discountKey}", "BLACK_FRIDAY")
                        .with(jwtAdmin(KC)).accept(APPLICATION_JSON))
                // ErrorManagementController maps DiscountTypeNotActiveException to 400 FUNCTIONAL via DISCOUNT_TYPE_NOT_ACTIVE
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCodeType").value("FUNCTIONAL"));
    }

    @Test
    void create_admin_ok_returns201() throws Exception {
        final DiscountCampaignCreateRequestDto req = new DiscountCampaignCreateRequestDto(
                "SUMMER_FLASH_SALE", DiscountCalculationType.PERCENTAGE, true,
                new BigDecimal("25.00"), null, null, null, null, null, 90);
        when(discountCampaignAdminService.create(any(DiscountCampaignCreateRequestDto.class)))
                .thenReturn(sample("SUMMER_FLASH_SALE", true));

        mockMvc.perform(post(BASE)
                        .with(jwtAdmin(KC))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .accept(APPLICATION_JSON)
                        .content(asJsonString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.discountKey").value("SUMMER_FLASH_SALE"));

        verify(discountCampaignAdminService).create(any(DiscountCampaignCreateRequestDto.class));
    }

    @Test
    void create_nonAdmin_forbidden() throws Exception {
        final DiscountCampaignCreateRequestDto req = new DiscountCampaignCreateRequestDto(
                "SUMMER_FLASH_SALE", DiscountCalculationType.PERCENTAGE, true,
                new BigDecimal("25.00"), null, null, null, null, null, 90);

        mockMvc.perform(post(BASE)
                        .with(jwtUser(KC))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .accept(APPLICATION_JSON)
                        .content(asJsonString(req)))
                .andExpect(status().isForbidden());

        verify(discountCampaignAdminService, never()).create(any());
    }

    @Test
    void create_blankDiscountKey_400() throws Exception {
        final DiscountCampaignCreateRequestDto req = new DiscountCampaignCreateRequestDto(
                " ", DiscountCalculationType.PERCENTAGE, true,
                new BigDecimal("25.00"), null, null, null, null, null, 90);

        mockMvc.perform(post(BASE)
                        .with(jwtAdmin(KC))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .accept(APPLICATION_JSON)
                        .content(asJsonString(req)))
                .andExpect(status().isBadRequest());

        verify(discountCampaignAdminService, never()).create(any());
    }

    @Test
    void update_admin_ok_evictsCacheAndReturnsUpdated() throws Exception {
        final DiscountCampaignUpdateRequestDto patch = new DiscountCampaignUpdateRequestDto(
                true, null, new BigDecimal("35.00"), null, null, null, null, null, null);
        when(discountCampaignAdminService.update(eq("BLACK_FRIDAY"), any(DiscountCampaignUpdateRequestDto.class)))
                .thenReturn(DiscountCampaignResponseDto.builder()
                        .uuid(UUID.randomUUID())
                        .discountKey("BLACK_FRIDAY")
                        .calculationType(DiscountCalculationType.PERCENTAGE)
                        .enabled(true)
                        .percentage(new BigDecimal("35.00"))
                        .build());

        mockMvc.perform(patch(BASE + "/{discountKey}", "BLACK_FRIDAY")
                        .with(jwtAdmin(KC))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .accept(APPLICATION_JSON)
                        .content(asJsonString(patch)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.percentage").value(35.00));

        verify(discountCampaignAdminService).update(eq("BLACK_FRIDAY"), any(DiscountCampaignUpdateRequestDto.class));
    }

    @Test
    void update_nonAdmin_forbidden() throws Exception {
        final DiscountCampaignUpdateRequestDto patch = new DiscountCampaignUpdateRequestDto(
                true, null, null, null, null, null, null, null, null);

        mockMvc.perform(patch(BASE + "/{discountKey}", "BLACK_FRIDAY")
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

        mockMvc.perform(patch(BASE + "/{discountKey}", "BLACK_FRIDAY")
                        .with(jwtAdmin(KC))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .accept(APPLICATION_JSON)
                        .content(asJsonString(patch)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void delete_admin_ok_returns204() throws Exception {
        mockMvc.perform(delete(BASE + "/{discountKey}", "BLACK_FRIDAY")
                        .with(jwtAdmin(KC))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(discountCampaignAdminService).deleteByDiscountKey("BLACK_FRIDAY");
    }

    @Test
    void delete_nonAdmin_forbidden() throws Exception {
        mockMvc.perform(delete(BASE + "/{discountKey}", "BLACK_FRIDAY")
                        .with(jwtUser(KC))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verify(discountCampaignAdminService, never()).deleteByDiscountKey(any());
    }

    @Test
    void delete_missing_returns400() throws Exception {
        org.mockito.Mockito.doThrow(new DiscountTypeNotActiveException("Discount campaign for BLACK_FRIDAY does not exist"))
                .when(discountCampaignAdminService).deleteByDiscountKey("BLACK_FRIDAY");

        mockMvc.perform(delete(BASE + "/{discountKey}", "BLACK_FRIDAY")
                        .with(jwtAdmin(KC))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCodeType").value("FUNCTIONAL"));
    }
}
