package com.novatech.cybertech.api.controllers.implementation.bankcard;

import com.novatech.cybertech.api.controllers.TestSecurityConfig;
import com.novatech.cybertech.api.controllers.implementation.BankCardManagementController;
import com.novatech.cybertech.api.error.model.ErrorResponseDto;
import com.novatech.cybertech.dto.request.user.BankCardCreationRequestDto;
import com.novatech.cybertech.dto.request.user.BankCardUpdateRequestDto;
import com.novatech.cybertech.dto.response.user.BankCardResponseDto;
import com.novatech.cybertech.exceptions.BankCardExpiredException;
import com.novatech.cybertech.exceptions.BankCardNotFoundException;
import com.novatech.cybertech.exceptions.NoDefaultBankCartSetException;
import com.novatech.cybertech.fixtures.dto.UserDtoFixtures;
import com.novatech.cybertech.services.core.BankCardManagementService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.json.JsonCompareMode.STRICT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @WebMvcTest slice for {@link BankCardManagementController}. Verifies endpoints, JSON envelopes,
 * DTO validation, JWT-subject forwarding to the service (skeptical: catches the
 * "passes the whole Jwt instead of subject String" regression), and that the F2-added
 * exception handlers map domain exceptions to the proper 4xx codes.
 */
@Slf4j
@Import({TestSecurityConfig.class})
@WebMvcTest(value = BankCardManagementController.class)
class BankCardManagementControllerTest {

    private static final String BASE = "/api/v1/services/bank-card";
    private static final String ADD = BASE + "/add";
    private static final String DELETE_USER = BASE + "/delete";
    private static final String UPDATE_USER = BASE + "/update";
    private static final String GET_ALL = BASE;
    private static final String GET_BY_UUID = BASE + "/{uuid}";
    private static final String CREATE_ADMIN = BASE;
    private static final String UPDATE_ADMIN = BASE;
    private static final String DELETE_ADMIN_BY_UUID = BASE + "/{uuid}";

    private static final String KEYCLOAK_ID = "keycloak-subject-id";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    BankCardManagementService bankCardService;

    // ---------- POST /add (user) ----------

    @Test
    void shouldAddBankCardSuccessfully() throws Exception {
        BankCardCreationRequestDto request = UserDtoFixtures.aValidBankCardCreationRequest();
        BankCardResponseDto response = UserDtoFixtures.aSampleBankCardResponse();

        when(bankCardService.addBankCard(eq(KEYCLOAK_ID), any(BankCardCreationRequestDto.class))).thenReturn(response);

        mockMvc.perform(post(ADD)
                        .with(jwtUser(KEYCLOAK_ID))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .accept(APPLICATION_JSON)
                        .content(asJsonString(request)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(response), STRICT));

        // Skeptical: the service must receive the JWT subject (a String), not the whole Jwt object.
        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        verify(bankCardService).addBankCard(subjectCaptor.capture(), any(BankCardCreationRequestDto.class));
        assertThat(subjectCaptor.getValue()).isEqualTo(KEYCLOAK_ID);
    }

    @Test
    void shouldFailAddingBankCardWhenDtoBadRequest() throws Exception {
        BankCardCreationRequestDto bad = new BankCardCreationRequestDto();

        mockMvc.perform(post(ADD)
                        .with(jwtUser(KEYCLOAK_ID))
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
    void shouldFailAddingBankCardWhenExpired() throws Exception {
        // BUG-002 (per F2): BankCardExpiredException now mapped to 400 BANK_CARD_EXPIRED.
        BankCardCreationRequestDto request = UserDtoFixtures.aValidBankCardCreationRequest();
        ErrorResponseDto errorResponseDto = ErrorResponseDto.builder()
                .message("Card already expired")
                .httpStatusCode(400)
                .errorCodeType(FUNCTIONAL)
                .build();

        when(bankCardService.addBankCard(eq(KEYCLOAK_ID), any(BankCardCreationRequestDto.class)))
                .thenThrow(new BankCardExpiredException("Card already expired"));

        mockMvc.perform(post(ADD)
                        .with(jwtUser(KEYCLOAK_ID))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .accept(APPLICATION_JSON)
                        .content(asJsonString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(errorResponseDto), STRICT));
    }

    @Test
    void shouldFailAddingBankCardWhenAnonymousCauseUnauthorized() throws Exception {
        BankCardCreationRequestDto request = UserDtoFixtures.aValidBankCardCreationRequest();

        mockMvc.perform(post(ADD)
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .accept(APPLICATION_JSON)
                        .content(asJsonString(request)))
                .andExpect(status().isUnauthorized());
    }

    // ---------- DELETE /delete (user) ----------

    @Test
    void shouldDeleteBankCardSuccessfully() throws Exception {
        doNothing().when(bankCardService).deleteBankCard(KEYCLOAK_ID);

        mockMvc.perform(delete(DELETE_USER)
                        .with(jwtUser(KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON))
                .andExpect(status().isNoContent());

        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        verify(bankCardService).deleteBankCard(subjectCaptor.capture());
        assertThat(subjectCaptor.getValue()).isEqualTo(KEYCLOAK_ID);
    }

    @Test
    void shouldFailDeletingBankCardWhenNoDefaultSet() throws Exception {
        // BUG-006 (per F2): NoDefaultBankCartSetException now mapped to 403 NO_DEFAULT_BANK_CARD_SET.
        ErrorResponseDto errorResponseDto = ErrorResponseDto.builder()
                .message("No default bank card set for the user")
                .httpStatusCode(403)
                .errorCodeType(FUNCTIONAL)
                .build();

        doThrow(new NoDefaultBankCartSetException("No default bank card set for the user"))
                .when(bankCardService).deleteBankCard(KEYCLOAK_ID);

        mockMvc.perform(delete(DELETE_USER)
                        .with(jwtUser(KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(errorResponseDto), STRICT));
    }

    @Test
    void shouldFailDeletingBankCardWhenAnonymousCauseUnauthorized() throws Exception {
        mockMvc.perform(delete(DELETE_USER)
                        .with(csrf())
                        .accept(APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    // ---------- PUT /update (user) ----------

    @Test
    void shouldUpdateBankCardSuccessfully() throws Exception {
        BankCardUpdateRequestDto request = UserDtoFixtures.aValidBankCardUpdateRequest();
        BankCardResponseDto response = UserDtoFixtures.aSampleBankCardResponse();

        when(bankCardService.updateBankCard(eq(KEYCLOAK_ID), any(BankCardUpdateRequestDto.class))).thenReturn(response);

        mockMvc.perform(put(UPDATE_USER)
                        .with(jwtUser(KEYCLOAK_ID))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .accept(APPLICATION_JSON)
                        .content(asJsonString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(response), STRICT));

        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        verify(bankCardService).updateBankCard(subjectCaptor.capture(), any(BankCardUpdateRequestDto.class));
        assertThat(subjectCaptor.getValue()).isEqualTo(KEYCLOAK_ID);
    }

    @Test
    void shouldFailUpdatingBankCardWhenDtoBadRequest() throws Exception {
        BankCardUpdateRequestDto bad = new BankCardUpdateRequestDto();

        mockMvc.perform(put(UPDATE_USER)
                        .with(jwtUser(KEYCLOAK_ID))
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
    void shouldFailUpdatingBankCardWhenNotFound() throws Exception {
        // BUG-003 (per F2): BankCardNotFoundException now mapped to 404 BANK_CARD_NOT_FOUND.
        BankCardUpdateRequestDto request = UserDtoFixtures.aValidBankCardUpdateRequest();
        ErrorResponseDto errorResponseDto = ErrorResponseDto.builder()
                .message("Bank card not found")
                .httpStatusCode(404)
                .errorCodeType(FUNCTIONAL)
                .build();

        when(bankCardService.updateBankCard(eq(KEYCLOAK_ID), any(BankCardUpdateRequestDto.class)))
                .thenThrow(new BankCardNotFoundException("Bank card not found"));

        mockMvc.perform(put(UPDATE_USER)
                        .with(jwtUser(KEYCLOAK_ID))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .accept(APPLICATION_JSON)
                        .content(asJsonString(request)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(errorResponseDto), STRICT));
    }

    // ---------- GET / (admin/all — BUG-161: now @PreAuthorize("hasRole('ADMIN')")) ----------

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
                // Page serialization is Spring-version-dependent; assert key invariants instead of full STRICT JSON.
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
        // BUG-161: admin CRUD endpoint now requires ROLE_ADMIN.
        mockMvc.perform(get(GET_ALL)
                        .with(jwtUser(KEYCLOAK_ID))
                        .accept(APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    // ---------- GET /{uuid} ----------

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
        // BUG-029 (per F2): MethodArgumentTypeMismatchException now handled → 400 (was 500).
        ErrorResponseDto errorResponseDto = ErrorResponseDto.builder()
                .message("Invalid value for parameter 'uuid'")
                .httpStatusCode(400)
                .errorCodeType(TECHNICAL)
                .build();

        mockMvc.perform(get(BASE + "/not-a-uuid")
                        .with(jwtAdmin(KEYCLOAK_ID))
                        .accept(APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(errorResponseDto), STRICT));
    }

    @Test
    void shouldFailGettingBankCardByUuidAsNonAdminCauseForbidden() throws Exception {
        // BUG-161: admin CRUD endpoint now requires ROLE_ADMIN.
        UUID cardUuid = UUID.randomUUID();
        mockMvc.perform(get(GET_BY_UUID, cardUuid)
                        .with(jwtUser(KEYCLOAK_ID))
                        .accept(APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    // ---------- POST / (admin create) ----------

    @Test
    void shouldCreateBankCardAdminSuccessfully() throws Exception {
        BankCardCreationRequestDto request = UserDtoFixtures.aValidBankCardCreationRequest();
        BankCardResponseDto response = UserDtoFixtures.aSampleBankCardResponse();

        when(bankCardService.create(any(BankCardCreationRequestDto.class))).thenReturn(response);

        mockMvc.perform(post(CREATE_ADMIN)
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

        mockMvc.perform(post(CREATE_ADMIN)
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
    void shouldFailCreatingBankCardAdminAsNonAdminCauseForbidden() throws Exception {
        // BUG-161: admin CRUD endpoint now requires ROLE_ADMIN.
        BankCardCreationRequestDto request = UserDtoFixtures.aValidBankCardCreationRequest();
        mockMvc.perform(post(CREATE_ADMIN)
                        .with(jwtUser(KEYCLOAK_ID))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .accept(APPLICATION_JSON)
                        .content(asJsonString(request)))
                .andExpect(status().isForbidden());
    }

    // ---------- PUT / (admin update) ----------

    @Test
    void shouldUpdateBankCardAdminSuccessfully() throws Exception {
        BankCardUpdateRequestDto request = UserDtoFixtures.aValidBankCardUpdateRequest();
        BankCardResponseDto response = UserDtoFixtures.aSampleBankCardResponse();

        when(bankCardService.update(any(BankCardUpdateRequestDto.class))).thenReturn(response);

        mockMvc.perform(put(UPDATE_ADMIN)
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
    void shouldFailUpdatingBankCardAdminAsNonAdminCauseForbidden() throws Exception {
        // BUG-161: admin CRUD endpoint now requires ROLE_ADMIN.
        BankCardUpdateRequestDto request = UserDtoFixtures.aValidBankCardUpdateRequest();
        mockMvc.perform(put(UPDATE_ADMIN)
                        .with(jwtUser(KEYCLOAK_ID))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .accept(APPLICATION_JSON)
                        .content(asJsonString(request)))
                .andExpect(status().isForbidden());
    }

    // ---------- DELETE /{uuid} (admin) ----------

    @Test
    void shouldDeleteBankCardByUuidAdminSuccessfully() throws Exception {
        UUID cardUuid = UUID.randomUUID();
        doNothing().when(bankCardService).deleteByUUID(cardUuid);

        mockMvc.perform(delete(DELETE_ADMIN_BY_UUID, cardUuid)
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

        mockMvc.perform(delete(DELETE_ADMIN_BY_UUID, cardUuid)
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

        mockMvc.perform(delete(DELETE_ADMIN_BY_UUID, cardUuid)
                        .with(csrf())
                        .accept(APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldFailDeletingBankCardByUuidAsNonAdminCauseForbidden() throws Exception {
        // BUG-161: admin CRUD endpoint now requires ROLE_ADMIN.
        UUID cardUuid = UUID.randomUUID();
        mockMvc.perform(delete(DELETE_ADMIN_BY_UUID, cardUuid)
                        .with(jwtUser(KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    // ---------- GET /all-mine (Frontend-gap #4) ----------

    @Test
    void shouldGetAllMineSuccessfully() throws Exception {
        BankCardResponseDto card = UserDtoFixtures.aSampleBankCardResponse();
        when(bankCardService.findAllMine(KEYCLOAK_ID)).thenReturn(List.of(card));

        mockMvc.perform(get(BASE + "/all-mine")
                        .with(jwtUser(KEYCLOAK_ID))
                        .accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(1));

        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        verify(bankCardService).findAllMine(subjectCaptor.capture());
        assertThat(subjectCaptor.getValue()).isEqualTo(KEYCLOAK_ID);
    }

    @Test
    void shouldGetAllMineReturnEmptyListWhenNoCard() throws Exception {
        when(bankCardService.findAllMine(KEYCLOAK_ID)).thenReturn(List.of());

        mockMvc.perform(get(BASE + "/all-mine")
                        .with(jwtUser(KEYCLOAK_ID))
                        .accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void shouldFailGetAllMineWhenAnonymousCauseUnauthorized() throws Exception {
        mockMvc.perform(get(BASE + "/all-mine")
                        .accept(APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }
}
