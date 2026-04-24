package com.novatech.cybertech.api.controllers.implementation.user;

import com.novatech.cybertech.api.controllers.TestSecurityConfig;
import com.novatech.cybertech.api.controllers.implementation.UserManagementAdminController;
import com.novatech.cybertech.api.error.ErrorManagementController;
import com.novatech.cybertech.api.error.model.ErrorResponseDto;
import com.novatech.cybertech.dto.request.user.UserCreateRequestDto;
import com.novatech.cybertech.dto.request.user.UserUpdateRequestDto;
import com.novatech.cybertech.dto.response.user.UserResponseDto;
import com.novatech.cybertech.exceptions.UserAlreadyExistsException;
import com.novatech.cybertech.exceptions.UserNotActiveException;
import com.novatech.cybertech.exceptions.UserNotFoundException;
import com.novatech.cybertech.fixtures.dto.UserDtoFixtures;
import com.novatech.cybertech.services.implementation.UserManagementServiceImp;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collection;
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
import static org.springframework.test.json.JsonCompareMode.LENIENT;
import static org.springframework.test.json.JsonCompareMode.STRICT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @WebMvcTest slice for {@link UserManagementAdminController}.
 *
 * <p>The controller is class-annotated {@code @PreAuthorize("hasRole('ADMIN')")}; W0's
 * {@code @EnableMethodSecurity} on {@link TestSecurityConfig} should enforce role on every
 * endpoint. Pinned bug status:
 * <ul>
 *   <li>BUG-015: {@link UserAlreadyExistsException} → 409 (handler verified).
 *       Asserted by {@link #shouldFailCreateUserAlreadyExistsAs409}.</li>
 *   <li>BUG-016: {@link UserNotActiveException} → 403 (handler verified).
 *       Asserted by {@link #shouldFailUpdateUserNotActiveAs403}.</li>
 *   <li>BUG-031: {@code AccessDeniedException}/{@code AuthorizationDeniedException} → 403.
 *       Asserted by every {@code shouldRejectXxxAsRoleUserReturning403}.</li>
 * </ul>
 */
@Import({TestSecurityConfig.class, ErrorManagementController.class})
@WebMvcTest(value = UserManagementAdminController.class)
class UserManagementAdminControllerTest {

    private static final String GET_ALL_USERS_ENDPOINT = "/api/v1/services/admin/user/get/all";
    private static final String CREATE_USER_ENDPOINT = "/api/v1/services/admin/user/create";
    private static final String UPDATE_USER_ENDPOINT = "/api/v1/services/admin/user/update";
    private static final String DELETE_USER_BY_UUID_ENDPOINT = "/api/v1/services/admin/user/delete/{userUuid}";
    private static final String REGISTER_AUTO_BULK_ENDPOINT = "/api/v1/services/admin/user/register/auto";

    private static final String ADMIN_KEYCLOAK_ID = "keycloak-admin";
    private static final String USER_KEYCLOAK_ID = "keycloak-user";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    UserManagementServiceImp userManagementServiceImp;

    // ---------- GET /get/all ----------

    @Test
    void shouldGetAllUsersAsAdminSuccessfully() throws Exception {
        final UserResponseDto u1 = UserDtoFixtures.aSampleUserResponse();
        final Page<UserResponseDto> page = new PageImpl<>(List.of(u1));

        when(userManagementServiceImp.getAll(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get(GET_ALL_USERS_ENDPOINT)
                        .with(jwtAdmin(ADMIN_KEYCLOAK_ID))
                        .accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(jsonPath("$.content[0].uuid").value(u1.getUuid().toString()))
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void shouldRejectGetAllUsersAsRoleUserReturning403() throws Exception {
        // BUG-031 verification: ROLE_USER hitting an admin-only endpoint must yield 403 FUNCTIONAL.
        final ErrorResponseDto error = ErrorResponseDto.builder()
                .message("Access denied")
                .httpStatusCode(403)
                .errorCodeType(FUNCTIONAL)
                .build();

        mockMvc.perform(get(GET_ALL_USERS_ENDPOINT)
                        .with(jwtUser(USER_KEYCLOAK_ID))
                        .accept(APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(error), STRICT));
    }

    @Test
    void shouldRejectGetAllUsersWhenAnonymousReturning401() throws Exception {
        mockMvc.perform(get(GET_ALL_USERS_ENDPOINT)
                        .accept(APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    // ---------- POST /create ----------

    @Test
    void shouldCreateUserAsAdminSuccessfully() throws Exception {
        final UserCreateRequestDto request = UserDtoFixtures.aValidCreateRequest();
        final UserResponseDto response = UserDtoFixtures.aSampleUserResponse();

        when(userManagementServiceImp.create(any(UserCreateRequestDto.class))).thenReturn(response);

        mockMvc.perform(post(CREATE_USER_ENDPOINT)
                        .with(jwtAdmin(ADMIN_KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON)
                        .content(asJsonString(request)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(response), STRICT));
    }

    @Test
    void shouldFailCreateUserCauseDtoBadRequestReturning400() throws Exception {
        final UserCreateRequestDto invalid = new UserCreateRequestDto();

        mockMvc.perform(post(CREATE_USER_ENDPOINT)
                        .with(jwtAdmin(ADMIN_KEYCLOAK_ID))
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
    void shouldFailCreateUserAlreadyExistsAs409() throws Exception {
        // BUG-015 verification.
        final UserCreateRequestDto request = UserDtoFixtures.aValidCreateRequest();
        final ErrorResponseDto error = ErrorResponseDto.builder()
                .message("User already exists")
                .httpStatusCode(409)
                .errorCodeType(FUNCTIONAL)
                .build();

        when(userManagementServiceImp.create(any(UserCreateRequestDto.class)))
                .thenThrow(new UserAlreadyExistsException("User already exists"));

        mockMvc.perform(post(CREATE_USER_ENDPOINT)
                        .with(jwtAdmin(ADMIN_KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON)
                        .content(asJsonString(request)))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(error), STRICT));
    }

    @Test
    void shouldRejectCreateUserAsRoleUserReturning403() throws Exception {
        // BUG-031 verification.
        final ErrorResponseDto error = ErrorResponseDto.builder()
                .message("Access denied")
                .httpStatusCode(403)
                .errorCodeType(FUNCTIONAL)
                .build();

        mockMvc.perform(post(CREATE_USER_ENDPOINT)
                        .with(jwtUser(USER_KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON)
                        .content(asJsonString(UserDtoFixtures.aValidCreateRequest())))
                .andExpect(status().isForbidden())
                .andExpect(content().json(asJsonString(error), STRICT));
    }

    // ---------- PATCH /update ----------

    @Test
    void shouldUpdateUserAsAdminSuccessfully() throws Exception {
        final UserUpdateRequestDto request = UserDtoFixtures.aValidUpdateRequest();
        final UserResponseDto response = UserDtoFixtures.aSampleUserResponseBuilder().uuid(request.getUuid()).build();

        when(userManagementServiceImp.update(any(UserUpdateRequestDto.class))).thenReturn(response);

        mockMvc.perform(patch(UPDATE_USER_ENDPOINT)
                        .with(jwtAdmin(ADMIN_KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON)
                        .content(asJsonString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(response), STRICT));
    }

    @Test
    void shouldFailUpdateUserCauseDtoBadRequestReturning400() throws Exception {
        // No uuid → @NotNull("User UUID cannot be null") triggers MethodArgumentNotValidException → 400.
        final UserUpdateRequestDto invalid = new UserUpdateRequestDto();

        mockMvc.perform(patch(UPDATE_USER_ENDPOINT)
                        .with(jwtAdmin(ADMIN_KEYCLOAK_ID))
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
    void shouldFailUpdateUserNotFoundReturning404() throws Exception {
        final UserUpdateRequestDto request = UserDtoFixtures.aValidUpdateRequest();
        final String message = "No user with the UUID: " + request.getUuid() + " found";
        final ErrorResponseDto error = ErrorResponseDto.builder()
                .message(message)
                .httpStatusCode(404)
                .errorCodeType(FUNCTIONAL)
                .build();

        when(userManagementServiceImp.update(any(UserUpdateRequestDto.class)))
                .thenThrow(new UserNotFoundException(message));

        mockMvc.perform(patch(UPDATE_USER_ENDPOINT)
                        .with(jwtAdmin(ADMIN_KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON)
                        .content(asJsonString(request)))
                .andExpect(status().isNotFound())
                .andExpect(content().json(asJsonString(error), STRICT));
    }

    @Test
    void shouldFailUpdateUserNotActiveAs403() throws Exception {
        // BUG-016 verification: UserNotActiveException → 403 FORBIDDEN (FUNCTIONAL).
        final UserUpdateRequestDto request = UserDtoFixtures.aValidUpdateRequest();
        final ErrorResponseDto error = ErrorResponseDto.builder()
                .message("User is not active")
                .httpStatusCode(403)
                .errorCodeType(FUNCTIONAL)
                .build();

        when(userManagementServiceImp.update(any(UserUpdateRequestDto.class)))
                .thenThrow(new UserNotActiveException("User is not active"));

        mockMvc.perform(patch(UPDATE_USER_ENDPOINT)
                        .with(jwtAdmin(ADMIN_KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON)
                        .content(asJsonString(request)))
                .andExpect(status().isForbidden())
                .andExpect(content().json(asJsonString(error), STRICT));
    }

    @Test
    void shouldRejectUpdateUserAsRoleUserReturning403() throws Exception {
        final ErrorResponseDto error = ErrorResponseDto.builder()
                .message("Access denied")
                .httpStatusCode(403)
                .errorCodeType(FUNCTIONAL)
                .build();

        mockMvc.perform(patch(UPDATE_USER_ENDPOINT)
                        .with(jwtUser(USER_KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON)
                        .content(asJsonString(UserDtoFixtures.aValidUpdateRequest())))
                .andExpect(status().isForbidden())
                .andExpect(content().json(asJsonString(error), STRICT));
    }

    // ---------- DELETE /delete/{uuid} ----------

    @Test
    void shouldDeleteUserByUuidAsAdminReturning204() throws Exception {
        final UUID uuid = UUID.randomUUID();
        doNothing().when(userManagementServiceImp).deleteByUUID(uuid);

        mockMvc.perform(delete(DELETE_USER_BY_UUID_ENDPOINT, uuid)
                        .with(jwtAdmin(ADMIN_KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON))
                .andExpect(status().isNoContent());

        verify(userManagementServiceImp).deleteByUUID(uuid);
    }

    @Test
    void shouldFailDeleteUserByUuidWhenNotFoundReturning404() throws Exception {
        final UUID uuid = UUID.randomUUID();
        final String message = "No user with the UUID: " + uuid + " found";
        final ErrorResponseDto error = ErrorResponseDto.builder()
                .message(message)
                .httpStatusCode(404)
                .errorCodeType(FUNCTIONAL)
                .build();

        doThrow(new UserNotFoundException(message)).when(userManagementServiceImp).deleteByUUID(uuid);

        mockMvc.perform(delete(DELETE_USER_BY_UUID_ENDPOINT, uuid)
                        .with(jwtAdmin(ADMIN_KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().json(asJsonString(error), STRICT));
    }

    @Test
    void shouldRejectDeleteUserByUuidAsRoleUserReturning403() throws Exception {
        final ErrorResponseDto error = ErrorResponseDto.builder()
                .message("Access denied")
                .httpStatusCode(403)
                .errorCodeType(FUNCTIONAL)
                .build();

        mockMvc.perform(delete(DELETE_USER_BY_UUID_ENDPOINT, UUID.randomUUID())
                        .with(jwtUser(USER_KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(content().json(asJsonString(error), STRICT));
    }

    // ---------- POST /register/auto (bulk dev seeder) ----------

    @Test
    void shouldRegisterAutoBulkAsAdminReturning201() throws Exception {
        // Tech-debt note: this endpoint generates 100 users via DataGenerator.generateUsers(100).
        // Production endpoint exposing a dev seeder — flag as tech-debt (already documented).
        final Collection<UserResponseDto> created = List.of(UserDtoFixtures.aSampleUserResponse());
        when(userManagementServiceImp.createAutomatically(any())).thenReturn(created);

        mockMvc.perform(post(REGISTER_AUTO_BULK_ENDPOINT)
                        .with(jwtAdmin(ADMIN_KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(APPLICATION_JSON))
                // Lenient: comparing a Collection<DTO> via Jackson is shape-stable but timestamps may diff.
                .andExpect(content().json(asJsonString(created), LENIENT));
    }

    @Test
    void shouldRejectRegisterAutoBulkAsRoleUserReturning403() throws Exception {
        final ErrorResponseDto error = ErrorResponseDto.builder()
                .message("Access denied")
                .httpStatusCode(403)
                .errorCodeType(FUNCTIONAL)
                .build();

        mockMvc.perform(post(REGISTER_AUTO_BULK_ENDPOINT)
                        .with(jwtUser(USER_KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(content().json(asJsonString(error), STRICT));
    }

    @Test
    void shouldRejectRegisterAutoBulkWhenAnonymousReturning401() throws Exception {
        mockMvc.perform(post(REGISTER_AUTO_BULK_ENDPOINT)
                        .with(csrf())
                        .accept(APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }
}
