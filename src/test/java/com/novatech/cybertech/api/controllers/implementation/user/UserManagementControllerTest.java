package com.novatech.cybertech.api.controllers.implementation.user;

import com.novatech.cybertech.api.controllers.TestSecurityConfig;
import com.novatech.cybertech.api.controllers.implementation.UserManagementController;
import com.novatech.cybertech.api.error.ErrorManagementController;
import com.novatech.cybertech.api.error.model.ErrorResponseDto;
import com.novatech.cybertech.dto.request.user.UserCreateRequestDto;
import com.novatech.cybertech.dto.request.user.UserSelfUpdateRequestDto;
import com.novatech.cybertech.dto.response.user.UserResponseDto;
import com.novatech.cybertech.exceptions.UserAlreadyExistsException;
import com.novatech.cybertech.exceptions.UserNotFoundException;
import com.novatech.cybertech.fixtures.dto.UserDtoFixtures;
import com.novatech.cybertech.services.core.UserManagementService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static com.novatech.cybertech.api.error.enumpackage.ErrorCodeType.FUNCTIONAL;
import static com.novatech.cybertech.api.error.enumpackage.ErrorCodeType.TECHNICAL;
import static com.novatech.cybertech.fixtures.support.JwtTestUtils.jwtAdmin;
import static com.novatech.cybertech.fixtures.support.JwtTestUtils.jwtUser;
import static com.novatech.cybertech.utils.TestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.json.JsonCompareMode.STRICT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @WebMvcTest slice for {@link UserManagementController}.
 *
 * <p>Verifies happy-path JSON shapes plus security boundaries on the user-facing controller.
 * Pinned bug status:
 * <ul>
 *   <li>BUG-015: {@link UserAlreadyExistsException} now mapped to 409 (handler verified in
 *       {@code ErrorManagementController}). Asserted live by
 *       {@link #shouldFailRegisterUserAlreadyExistsAs409}.</li>
 *   <li>BUG-201 — <b>CLOSED</b> (SA-Fix-4, 2026-04-23): {@code POST /register/auto/single}
 *       now requires ADMIN. The method is annotated {@code @PreAuthorize("hasRole('ADMIN')")}
 *       on {@link UserManagementController#registerAuto()} and the URL pattern in
 *       {@code SecurityConfig#PUBLIC_URLS} is narrowed to the exact {@code /register} path
 *       (no wildcard), so anonymous calls fall through to {@code anyRequest().authenticated()}.
 *       Asserted by {@link #shouldRejectRegisterAutoSingleWhenAnonymousAfterBug201Fix} and
 *       {@link #shouldRejectRegisterAutoSingleAsRoleUserReturning403}; happy-path stays
 *       covered by {@link #shouldRegisterAutoSingleAsAdminReturning201}.</li>
 * </ul>
 */
@Import({TestSecurityConfig.class, ErrorManagementController.class})
@WebMvcTest(value = UserManagementController.class)
class UserManagementControllerTest {

    private static final String GET_USER_BY_UUID_ENDPOINT = "/api/v1/services/user/get/{userUuid}";
    private static final String REGISTER_ENDPOINT = "/api/v1/services/user/register";
    private static final String REGISTER_AUTO_SINGLE_ENDPOINT = "/api/v1/services/user/register/auto/single";
    private static final String HEALTH_CHECK_ENDPOINT = "/api/v1/services/user/ok";
    private static final String UPDATE_ME_ENDPOINT = "/api/v1/services/user/me";

    private static final String KEYCLOAK_ID = "keycloak-test-subject";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    UserManagementService userManagementServiceImp;

    // ---------- GET /get/{uuid} ----------

    @Test
    void shouldGetUserByUuidSuccessfullyWhenCallerIsOwner() throws Exception {
        // BUG-IDOR-D2: a USER may only read their own profile. The fixture's keycloakId is
        // pinned to the JWT subject so the ownership check passes.
        final UUID userUuid = UUID.randomUUID();
        final UserResponseDto response = UserDtoFixtures.aSampleUserResponseBuilder()
                .uuid(userUuid)
                .keycloakId(KEYCLOAK_ID)
                .build();

        when(userManagementServiceImp.getByUUID(userUuid)).thenReturn(response);

        mockMvc.perform(get(GET_USER_BY_UUID_ENDPOINT, userUuid)
                        .with(jwtUser(KEYCLOAK_ID))
                        .contentType(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(response), STRICT));
    }

    @Test
    void shouldGetUserByUuidSuccessfullyWhenCallerIsAdmin() throws Exception {
        // BUG-IDOR-D2: ADMINs bypass the ownership check.
        final UUID userUuid = UUID.randomUUID();
        final UserResponseDto response = UserDtoFixtures.aSampleUserResponseBuilder()
                .uuid(userUuid)
                .keycloakId("some-other-user-keycloak-id")
                .build();

        when(userManagementServiceImp.getByUUID(userUuid)).thenReturn(response);

        mockMvc.perform(get(GET_USER_BY_UUID_ENDPOINT, userUuid)
                        .with(jwtAdmin("admin-id"))
                        .contentType(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(response), STRICT));
    }

    @Test
    void shouldFailGetUserByUuidWhenCallerNotOwnerReturning403() throws Exception {
        // BUG-IDOR-D2: a USER asking for someone else's profile gets 403.
        final UUID userUuid = UUID.randomUUID();
        final UserResponseDto response = UserDtoFixtures.aSampleUserResponseBuilder()
                .uuid(userUuid)
                .keycloakId("a-different-keycloak-id")
                .build();

        when(userManagementServiceImp.getByUUID(userUuid)).thenReturn(response);

        mockMvc.perform(get(GET_USER_BY_UUID_ENDPOINT, userUuid)
                        .with(jwtUser(KEYCLOAK_ID))
                        .contentType(APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldFailGetUserByUuidWhenNotFoundReturning404() throws Exception {
        final UUID userUuid = UUID.randomUUID();
        final ErrorResponseDto error = ErrorResponseDto.builder()
                .message("No user with the UUID: " + userUuid + " found")
                .httpStatusCode(404)
                .errorCodeType(FUNCTIONAL)
                .build();

        when(userManagementServiceImp.getByUUID(userUuid))
                .thenThrow(new UserNotFoundException("No user with the UUID: " + userUuid + " found"));

        mockMvc.perform(get(GET_USER_BY_UUID_ENDPOINT, userUuid)
                        .with(jwtUser(KEYCLOAK_ID))
                        .contentType(APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(error), STRICT));
    }

    @Test
    void shouldFailGetUserByUuidWhenAnonymousReturning401() throws Exception {
        // GET /get/{uuid} is NOT in the public URL whitelist → anonymous = 401.
        mockMvc.perform(get(GET_USER_BY_UUID_ENDPOINT, UUID.randomUUID())
                        .contentType(APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    // ---------- POST /register ----------

    @Test
    void shouldRegisterUserSuccessfullyReturning201WithMapShape() throws Exception {
        // Bug 6 fix: anonymous /register response body NO longer carries keycloakId — the
        // server-built Map.of(...) returns id + email + username only. Asserting the new shape.
        final UUID createdUuid = UUID.randomUUID();
        final String keycloakId = "keycloak-" + UUID.randomUUID();

        final UserCreateRequestDto request = UserDtoFixtures.aValidCreateRequest();
        final UserResponseDto created = UserDtoFixtures.aSampleUserResponseBuilder()
                .uuid(createdUuid)
                .keycloakId(keycloakId)
                .email("user@example.com")
                .username("jane.doe")
                .build();

        when(userManagementServiceImp.create(any(UserCreateRequestDto.class))).thenReturn(created);

        // /register is whitelisted in TestSecurityConfig → reachable without auth.
        mockMvc.perform(post(REGISTER_ENDPOINT)
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON)
                        .content(asJsonString(request)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(createdUuid.toString()))
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.username").value("jane.doe"))
                .andExpect(jsonPath("$.keycloakId").doesNotExist());
    }

    @Test
    void registerResponseShouldNotContainKeycloakId() throws Exception {
        // Bug 6 explicit pin: the anonymous-public signup endpoint MUST NOT leak the Keycloak
        // subject. Even when the service returns a DTO carrying keycloakId, the controller
        // strips it from the response body.
        final UserCreateRequestDto request = UserDtoFixtures.aValidCreateRequest();
        final UserResponseDto created = UserDtoFixtures.aSampleUserResponseBuilder()
                .keycloakId("keycloak-secret-id")
                .build();
        when(userManagementServiceImp.create(any(UserCreateRequestDto.class))).thenReturn(created);

        mockMvc.perform(post(REGISTER_ENDPOINT)
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON)
                        .content(asJsonString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.keycloakId").doesNotExist());
    }

    @Test
    void shouldFailRegisterCauseDtoBadRequestReturning400() throws Exception {
        final UserCreateRequestDto invalid = new UserCreateRequestDto();

        mockMvc.perform(post(REGISTER_ENDPOINT)
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON)
                        .content(asJsonString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(jsonPath("$.message", startsWith("Validation failed:")))
                .andExpect(jsonPath("$.httpStatusCode").value(400))
                .andExpect(jsonPath("$.errorCodeType").value("TECHNICAL"));
    }

    @Test
    void shouldFailRegisterUserAlreadyExistsAs409() throws Exception {
        // BUG-015 verification: the dedicated @ExceptionHandler in ErrorManagementController
        // maps UserAlreadyExistsException → 409 CONFLICT (FUNCTIONAL).
        final UserCreateRequestDto request = UserDtoFixtures.aValidCreateRequest();
        final ErrorResponseDto error = ErrorResponseDto.builder()
                .message("User already exists")
                .httpStatusCode(409)
                .errorCodeType(FUNCTIONAL)
                .build();

        when(userManagementServiceImp.create(any(UserCreateRequestDto.class)))
                .thenThrow(new UserAlreadyExistsException("User already exists"));

        mockMvc.perform(post(REGISTER_ENDPOINT)
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON)
                        .content(asJsonString(request)))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(error), STRICT));
    }

    // ---------- POST /register/auto/single ----------

    @Test
    void shouldRejectRegisterAutoSingleWhenAnonymousAfterBug201Fix() throws Exception {
        // BUG-201 — CLOSED. registerAuto() is now @PreAuthorize("hasRole('ADMIN')") AND the
        // SecurityConfig#PUBLIC_URLS whitelist no longer covers /register/auto/** (only the
        // exact /register path is anonymous). The URL is now `anyRequest().authenticated()`
        // → CustomAuthenticationEntryPoint translates a missing JWT into 401.
        mockMvc.perform(post(REGISTER_AUTO_SINGLE_ENDPOINT)
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectRegisterAutoSingleAsRoleUserReturning403() throws Exception {
        // BUG-201 — even an authenticated non-ADMIN must be rejected (403) by @PreAuthorize.
        mockMvc.perform(post(REGISTER_AUTO_SINGLE_ENDPOINT)
                        .with(jwtUser(KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRegisterAutoSingleAsAdminReturning201() throws Exception {
        // Admin call still works — covers the happy path regardless of BUG-201 wiring.
        // Wave 3 regression-fix: response body is a Map.of(id, keycloakId, email, username)
        // (mirrors the /register endpoint) so the synthetic user's keycloakId is exposed —
        // the @JsonIgnore on UserResponseDto.keycloakId would have suppressed it otherwise.
        final UserResponseDto created = UserDtoFixtures.aSampleUserResponse();
        when(userManagementServiceImp.create(any(UserCreateRequestDto.class))).thenReturn(created);

        mockMvc.perform(post(REGISTER_AUTO_SINGLE_ENDPOINT)
                        .with(jwtAdmin(KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(created.getUuid().toString()))
                .andExpect(jsonPath("$.keycloakId").value(created.getKeycloakId()))
                .andExpect(jsonPath("$.email").value(created.getEmail()))
                .andExpect(jsonPath("$.username").value(created.getUsername()));
    }

    // ---------- GET /ok (health) ----------

    @Test
    void shouldReturnHealthCheckOk() throws Exception {
        // /ok is not whitelisted but doesn't throw — must be reachable to authenticated callers.
        mockMvc.perform(get(HEALTH_CHECK_ENDPOINT)
                        .with(jwtUser(KEYCLOAK_ID))
                        .accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON));
    }

    @Test
    void shouldRejectHealthCheckWhenAnonymousReturning401() throws Exception {
        // /ok is not in PUBLIC_URLS → anonymous calls produce 401 via the auth entry point.
        mockMvc.perform(get(HEALTH_CHECK_ENDPOINT)
                        .accept(APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    // ---------- PATCH /me (Frontend-gap #3) ----------

    @Test
    void updateMeShouldPatchSelfProfile() throws Exception {
        // Happy path: the JWT subject reaches the service unchanged and the response body is the mapped DTO.
        final UserSelfUpdateRequestDto dto = UserSelfUpdateRequestDto.builder()
                .firstName("Alice")
                .lastName("Doe")
                .phoneNumber("+33611111111")
                .address("42 rue de Test")
                .build();
        final UserResponseDto response = UserDtoFixtures.aSampleUserResponseBuilder()
                .firstName("Alice")
                .lastName("Doe")
                .build();

        when(userManagementServiceImp.updateMe(eq(KEYCLOAK_ID), any(UserSelfUpdateRequestDto.class)))
                .thenReturn(response);

        mockMvc.perform(patch(UPDATE_ME_ENDPOINT)
                        .with(jwtUser(KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON)
                        .content(asJsonString(dto)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(jsonPath("$.firstName").value("Alice"))
                .andExpect(jsonPath("$.lastName").value("Doe"));

        // Skeptical: subject (String) — NOT the whole Jwt — reaches the service.
        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<UserSelfUpdateRequestDto> dtoCaptor = ArgumentCaptor.forClass(UserSelfUpdateRequestDto.class);
        verify(userManagementServiceImp).updateMe(subjectCaptor.capture(), dtoCaptor.capture());
        assertThat(subjectCaptor.getValue()).isEqualTo(KEYCLOAK_ID);
        assertThat(dtoCaptor.getValue().getFirstName()).isEqualTo("Alice");
        assertThat(dtoCaptor.getValue().getPhoneNumber()).isEqualTo("+33611111111");
    }

    @Test
    void updateMeShouldRequireAuth() throws Exception {
        // Anonymous = 401 via CustomAuthenticationEntryPoint (path is not in PUBLIC_URLS).
        final UserSelfUpdateRequestDto dto = UserSelfUpdateRequestDto.builder().firstName("Alice").build();

        mockMvc.perform(patch(UPDATE_ME_ENDPOINT)
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON)
                        .content(asJsonString(dto)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateMeShouldReturn400OnInvalidPayload() throws Exception {
        // @Size(min=2) on firstName + lastName: a 1-char value triggers Bean Validation → 400 TECHNICAL.
        final UserSelfUpdateRequestDto invalid = UserSelfUpdateRequestDto.builder()
                .firstName("A")
                .lastName("Z")
                .build();

        mockMvc.perform(patch(UPDATE_ME_ENDPOINT)
                        .with(jwtUser(KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON)
                        .content(asJsonString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(jsonPath("$.message", startsWith("Validation failed:")))
                .andExpect(jsonPath("$.httpStatusCode").value(400))
                .andExpect(jsonPath("$.errorCodeType").value("TECHNICAL"));
    }

    @Test
    void updateMeShouldRejectAdminFields() throws Exception {
        // The DTO surface is the contract: it has NO role / status / email / username fields, so even
        // when the request body smuggles those, Jackson silently drops them and the captured DTO must
        // not carry any privilege-elevation hint. The skeptical assertion catches a regression where
        // someone might accidentally widen UserSelfUpdateRequestDto to include admin-owned fields.
        final UserResponseDto response = UserDtoFixtures.aSampleUserResponse();
        when(userManagementServiceImp.updateMe(eq(KEYCLOAK_ID), any(UserSelfUpdateRequestDto.class)))
                .thenReturn(response);

        final String smuggledBody = "{"
                + "\"firstName\":\"Alice\","
                + "\"lastName\":\"Doe\","
                + "\"role\":\"ADMIN\","
                + "\"status\":\"ACTIVE\","
                + "\"email\":\"hacker@example.com\","
                + "\"username\":\"root\""
                + "}";

        mockMvc.perform(patch(UPDATE_ME_ENDPOINT)
                        .with(jwtUser(KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON)
                        .content(smuggledBody))
                .andExpect(status().isOk());

        ArgumentCaptor<UserSelfUpdateRequestDto> dtoCaptor = ArgumentCaptor.forClass(UserSelfUpdateRequestDto.class);
        verify(userManagementServiceImp).updateMe(eq(KEYCLOAK_ID), dtoCaptor.capture());
        // The DTO surface has no admin-only field; reflection check via toString suffices to pin
        // that the published surface stayed minimal.
        final String dtoString = dtoCaptor.getValue().toString();
        assertThat(dtoString).contains("firstName=Alice", "lastName=Doe");
        assertThat(dtoString)
                .as("UserSelfUpdateRequestDto must NOT expose role/status/email/username — keeps self-service safe from privilege elevation")
                .doesNotContainIgnoringCase("role=")
                .doesNotContainIgnoringCase("status=")
                .doesNotContainIgnoringCase("email=")
                .doesNotContainIgnoringCase("username=");
    }
}
