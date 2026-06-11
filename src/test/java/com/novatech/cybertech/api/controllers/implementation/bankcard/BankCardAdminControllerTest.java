package com.novatech.cybertech.api.controllers.implementation.bankcard;

import com.novatech.cybertech.api.controllers.TestSecurityConfig;
import com.novatech.cybertech.api.controllers.implementation.BankCardAdminController;
import com.novatech.cybertech.api.error.model.ErrorResponseDto;
import com.novatech.cybertech.dto.request.user.BankCardCreationRequestDto;
import com.novatech.cybertech.dto.request.user.BankCardUpdateRequestDto;
import com.novatech.cybertech.dto.response.user.BankCardResponseDto;
import com.novatech.cybertech.exceptions.BankCardNotFoundException;
import com.novatech.cybertech.fixtures.dto.UserDtoFixtures;
import com.novatech.cybertech.services.core.BankCardManagementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static com.novatech.cybertech.api.error.enumpackage.ErrorCodeType.FUNCTIONAL;
import static com.novatech.cybertech.api.error.enumpackage.ErrorCodeType.TECHNICAL;
import static com.novatech.cybertech.fixtures.support.JwtTestUtils.jwtAdmin;
import static com.novatech.cybertech.fixtures.support.JwtTestUtils.jwtUser;
import static com.novatech.cybertech.utils.TestUtils.asJsonString;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.json.JsonCompareMode.STRICT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @WebMvcTest slice for {@link BankCardAdminController}. Verifies the admin-only bank-card
 * endpoints at their new /admin/bank-card paths: ADMIN reaches them, USER is forbidden (class-level
 * @PreAuthorize), anonymous is unauthorized, and the F2 exception handlers still map domain
 * exceptions to the proper 4xx codes. Ported from BankCardManagementControllerTest's admin section.
 */
@Import({TestSecurityConfig.class})
@WebMvcTest(value = BankCardAdminController.class)
class BankCardAdminControllerTest {

    private static final String BASE = "/api/v1/services/admin/bank-card";
    private static final String GET_ALL = BASE + "/get/all";
    private static final String GET_BY_UUID = BASE + "/get/{uuid}";
    private static final String CREATE = BASE + "/create";
    private static final String UPDATE = BASE + "/update";
    private static final String DELETE_BY_UUID = BASE + "/delete/{uuid}";

    private static final String KEYCLOAK_ID = "keycloak-subject-id";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    BankCardManagementService bankCardService;

    // ---------- GET /get/all ----------

    @Test
    void shouldGetAllBankCardsSuccessfully() throws Exception {
        BankCardResponseDto a = UserDtoFixtures.aSampleBankCardResponse();
        BankCardResponseDto b = UserDtoFixtures.aSampleBankCardResponse();
        Page<BankCardResponseDto> page = new PageImpl<>(List.of(a, b), PageRequest.of(0, 10), 2);

        when(bankCardService.getAll(any())).thenReturn(page);

        mockMvc.perform(get(GET_ALL)
                        .with(jwtAdmin(KEYCLOAK_ID))
                        .accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(jsonPath("$.content", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].uuid").value(a.getUuid().toString()))
                .andExpect(jsonPath("$.content[1].uuid").value(b.getUuid().toString()));
    }

    @Test
    void shouldFailGettingAllBankCardsWhenAnonymousCauseUnauthorized() throws Exception {
        mockMvc.perform(get(GET_ALL)
                        .accept(APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldFailGettingAllBankCardsAsNonAdminCauseForbidden() throws Exception {
        mockMvc.perform(get(GET_ALL)
                        .with(jwtUser(KEYCLOAK_ID))
                        .accept(APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    // ---------- GET /get/{uuid} ----------

    @Test
    void shouldGetBankCardByUuidSuccessfully() throws Exception {
        UUID cardUuid = UUID.randomUUID();
        BankCardResponseDto response = UserDtoFixtures.aSampleBankCardResponseBuilder().uuid(cardUuid).build();

        when(bankCardService.getByUUID(cardUuid)).thenReturn(response);

        mockMvc.perform(get(GET_BY_UUID, cardUuid)
                        .with(jwtAdmin(KEYCLOAK_ID))
                        .accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(response), STRICT));
    }

    @Test
    void shouldFailGettingBankCardByUuidWhenNotFound() throws Exception {
        UUID cardUuid = UUID.randomUUID();
        ErrorResponseDto errorResponseDto = ErrorResponseDto.builder()
                .message("Bank card not found by UUID")
                .httpStatusCode(404)
                .errorCodeType(FUNCTIONAL)
                .build();

        when(bankCardService.getByUUID(cardUuid))
                .thenThrow(new BankCardNotFoundException("Bank card not found by UUID"));

        mockMvc.perform(get(GET_BY_UUID, cardUuid)
                        .with(jwtAdmin(KEYCLOAK_ID))
                        .accept(APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(errorResponseDto), STRICT));
    }

    @Test
    void shouldFailGettingBankCardByUuidWhenPathUuidMalformed() throws Exception {
        ErrorResponseDto errorResponseDto = ErrorResponseDto.builder()
                .message("Invalid value for parameter 'uuid'")
                .httpStatusCode(400)
                .errorCodeType(TECHNICAL)
                .build();

        mockMvc.perform(get(BASE + "/get/not-a-uuid")
                        .with(jwtAdmin(KEYCLOAK_ID))
                        .accept(APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(errorResponseDto), STRICT));
    }

    @Test
    void shouldFailGettingBankCardByUuidAsNonAdminCauseForbidden() throws Exception {
        UUID cardUuid = UUID.randomUUID();
        mockMvc.perform(get(GET_BY_UUID, cardUuid)
                        .with(jwtUser(KEYCLOAK_ID))
                        .accept(APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    // ---------- POST /create ----------

    @Test
    void shouldCreateBankCardAdminSuccessfully() throws Exception {
        BankCardCreationRequestDto request = UserDtoFixtures.aValidBankCardCreationRequest();
        BankCardResponseDto response = UserDtoFixtures.aSampleBankCardResponse();

        when(bankCardService.create(any(BankCardCreationRequestDto.class))).thenReturn(response);

        mockMvc.perform(post(CREATE)
                        .with(jwtAdmin(KEYCLOAK_ID))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .accept(APPLICATION_JSON)
                        .content(asJsonString(request)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(response), STRICT));
    }

    @Test
    void shouldFailCreatingBankCardAdminWhenDtoBadRequest() throws Exception {
        BankCardCreationRequestDto bad = new BankCardCreationRequestDto();

        mockMvc.perform(post(CREATE)
                        .with(jwtAdmin(KEYCLOAK_ID))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .accept(APPLICATION_JSON)
                        .content(asJsonString(bad)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(jsonPath("$.message", startsWith("Validation failed:")))
                .andExpect(jsonPath("$.httpStatusCode").value(400))
                .andExpect(jsonPath("$.errorCodeType").value("TECHNICAL"));
    }

    @Test
    void shouldFailCreatingBankCardAdminWhenAnonymousCauseUnauthorized() throws Exception {
        mockMvc.perform(post(CREATE)
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .accept(APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldFailCreatingBankCardAdminAsNonAdminCauseForbidden() throws Exception {
        BankCardCreationRequestDto request = UserDtoFixtures.aValidBankCardCreationRequest();
        mockMvc.perform(post(CREATE)
                        .with(jwtUser(KEYCLOAK_ID))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .accept(APPLICATION_JSON)
                        .content(asJsonString(request)))
                .andExpect(status().isForbidden());
    }

    // ---------- PATCH /update ----------

    @Test
    void shouldUpdateBankCardAdminSuccessfully() throws Exception {
        BankCardUpdateRequestDto request = UserDtoFixtures.aValidBankCardUpdateRequest();
        BankCardResponseDto response = UserDtoFixtures.aSampleBankCardResponse();

        when(bankCardService.update(any(BankCardUpdateRequestDto.class))).thenReturn(response);

        mockMvc.perform(patch(UPDATE)
                        .with(jwtAdmin(KEYCLOAK_ID))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .accept(APPLICATION_JSON)
                        .content(asJsonString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(response), STRICT));
    }

    @Test
    void shouldFailUpdatingBankCardAdminWhenAnonymousCauseUnauthorized() throws Exception {
        mockMvc.perform(patch(UPDATE)
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .accept(APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldFailUpdatingBankCardAdminAsNonAdminCauseForbidden() throws Exception {
        BankCardUpdateRequestDto request = UserDtoFixtures.aValidBankCardUpdateRequest();
        mockMvc.perform(patch(UPDATE)
                        .with(jwtUser(KEYCLOAK_ID))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .accept(APPLICATION_JSON)
                        .content(asJsonString(request)))
                .andExpect(status().isForbidden());
    }

    // ---------- DELETE /delete/{uuid} ----------

    @Test
    void shouldDeleteBankCardByUuidAdminSuccessfully() throws Exception {
        UUID cardUuid = UUID.randomUUID();
        doNothing().when(bankCardService).deleteByUUID(cardUuid);

        mockMvc.perform(delete(DELETE_BY_UUID, cardUuid)
                        .with(jwtAdmin(KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON))
                .andExpect(status().isNoContent());

        verify(bankCardService).deleteByUUID(cardUuid);
    }

    @Test
    void shouldFailDeletingBankCardByUuidWhenNotFound() throws Exception {
        UUID cardUuid = UUID.randomUUID();
        ErrorResponseDto errorResponseDto = ErrorResponseDto.builder()
                .message("Bank card not found for delete")
                .httpStatusCode(404)
                .errorCodeType(FUNCTIONAL)
                .build();

        doThrow(new BankCardNotFoundException("Bank card not found for delete"))
                .when(bankCardService).deleteByUUID(cardUuid);

        mockMvc.perform(delete(DELETE_BY_UUID, cardUuid)
                        .with(jwtAdmin(KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(errorResponseDto), STRICT));
    }

    @Test
    void shouldFailDeletingBankCardByUuidWhenAnonymousCauseUnauthorized() throws Exception {
        UUID cardUuid = UUID.randomUUID();

        mockMvc.perform(delete(DELETE_BY_UUID, cardUuid)
                        .with(csrf())
                        .accept(APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldFailDeletingBankCardByUuidAsNonAdminCauseForbidden() throws Exception {
        UUID cardUuid = UUID.randomUUID();
        mockMvc.perform(delete(DELETE_BY_UUID, cardUuid)
                        .with(jwtUser(KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }
}
