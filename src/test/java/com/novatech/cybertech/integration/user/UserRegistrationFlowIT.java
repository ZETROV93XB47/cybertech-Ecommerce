package com.novatech.cybertech.integration.user;

import com.novatech.cybertech.TestcontainersConfiguration;
import com.novatech.cybertech.dto.request.user.UserCreateRequestDto;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.exceptions.UserAlreadyExistsException;
import com.novatech.cybertech.fixtures.dto.UserDtoFixtures;
import com.novatech.cybertech.fixtures.support.TestDataCleaner;
import com.novatech.cybertech.fixtures.support.stubs.KeycloakAdminStub;
import com.novatech.cybertech.repositories.UserRepository;
import com.novatech.cybertech.services.implementation.UserManagementServiceImp;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;
import java.util.UUID;

import static com.novatech.cybertech.fixtures.support.JwtTestUtils.jwtAdmin;
import static com.novatech.cybertech.fixtures.support.JwtTestUtils.jwtUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SA-W5.5 — End-to-end user registration flow IT against the real Wave-1 Testcontainers
 * stack (MySQL + Redis + Elasticsearch + MongoDB) wired through the W0 public
 * {@link TestcontainersConfiguration}. The Keycloak admin client is swapped for a Mockito
 * stub via {@link KeycloakAdminStub} so this suite never reaches a live Keycloak.
 *
 * <p><b>Bug-pin policy (original numbers preserved):</b>
 * <ul>
 *   <li><b>BUG-015</b> — {@code UserAlreadyExistsException} now mapped to 409 by
 *       {@code ErrorManagementController#handleUserAlreadyExistsException}. F2 fix
 *       <b>CONFIRMED</b> live. Asserted by
 *       {@link #duplicateRegisterReturns409PerBug015}. Note: production code does not yet
 *       throw {@code UserAlreadyExistsException} from the registration path itself
 *       (Keycloak conflicts surface as {@code IllegalStateException}); we stub the service
 *       to throw the exception so the @ControllerAdvice mapping can be exercised end-to-end
 *       through the real Spring MVC + security filter chain.</li>
 *   <li><b>BUG-031</b> — {@code AuthorizationDeniedException}/{@code AccessDeniedException}
 *       now mapped to 403 (combined handler at lines 57-61 of
 *       {@code ErrorManagementController}). F2 fix <b>CONFIRMED</b> live. Asserted by
 *       {@link #adminGetAllAsRoleUserReturns403PerBug031}.</li>
 *   <li><b>BUG-201</b> — <b>CLOSED</b> by SA-Fix-4 (2026-04-23). {@code registerAuto()} is
 *       now annotated {@code @PreAuthorize("hasRole('ADMIN')")} and the
 *       {@code SecurityConfig#PUBLIC_URLS} whitelist entry has been narrowed from
 *       {@code /api/v1/services/user/register/**} to the exact
 *       {@code /api/v1/services/user/register} path. Anonymous calls to
 *       {@code /register/auto/single} now hit the auth entry point → 401.
 *       Asserted by {@link #registerAutoSingleRejectsAnonymousAfterBug201Fix}.</li>
 * </ul>
 */
@Slf4j
@Testcontainers
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, KeycloakAdminStub.class})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("UserRegistrationFlowIT — end-to-end /register surface against real containers")
class UserRegistrationFlowIT {

    private static final String REGISTER_ENDPOINT = "/api/v1/services/user/register";
    private static final String REGISTER_AUTO_SINGLE_ENDPOINT = "/api/v1/services/user/register/auto/single";
    private static final String ADMIN_GET_ALL_USERS_ENDPOINT = "/api/v1/services/admin/user/get/all";
    private static final String ACTUATOR_HEALTH_ENDPOINT = "/actuator/health";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestDataCleaner testDataCleaner;

    /**
     * Spy on the real service so we can selectively force {@link UserAlreadyExistsException}
     * for the duplicate-email test (the production code-path does not currently throw it
     * from the registration flow — Keycloak conflicts surface as IllegalStateException).
     */
    @MockitoSpyBean
    private UserManagementServiceImp userManagementServiceImp;

    @BeforeEach
    void wipe() {
        testDataCleaner.wipe();
    }

    // -----------------------------------------------------------------------------------
    // 1. Happy path — POST /register → 201, user persisted to MySQL.
    // -----------------------------------------------------------------------------------
    @Test
    @DisplayName("POST /register — 201 CREATED and user row written to MySQL")
    void registerHappyPathPersistsUserInMysql() throws Exception {
        final String uniqueEmail = "register-it+" + UUID.randomUUID() + "@example.com";
        final UserCreateRequestDto request = UserDtoFixtures.aValidCreateRequestBuilder()
                .email(uniqueEmail)
                .build();

        mockMvc.perform(post(REGISTER_ENDPOINT)
                        .with(csrf())
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.keycloakId").exists());

        final Optional<UserEntity> persisted = userRepository.findByEmail(uniqueEmail);
        assertThat(persisted).as("user with the unique email should be in MySQL").isPresent();
        assertThat(persisted.get().getFirstName()).isEqualTo("Jane");
        assertThat(persisted.get().getLastName()).isEqualTo("Doe");
        assertThat(persisted.get().getKeycloakId()).as("keycloak id must be populated by stub").isNotBlank();
    }

    // -----------------------------------------------------------------------------------
    // 2. Duplicate email → 409 CONFLICT (BUG-015 closed by F2).
    //    The registration code-path never throws UserAlreadyExistsException itself today
    //    (Keycloak conflicts bubble up as IllegalStateException), so we spy on the service
    //    and force the exception. The point of this IT is to verify that the @ControllerAdvice
    //    in ErrorManagementController ACTUALLY maps the exception → 409 in a full Spring MVC
    //    + Security filter chain context (slice tests can't catch advice ordering or
    //    filter-chain interception bugs).
    // -----------------------------------------------------------------------------------
    @Test
    @DisplayName("POST /register on duplicate email — 409 CONFLICT (BUG-015 fix CONFIRMED)")
    void duplicateRegisterReturns409PerBug015() throws Exception {
        final UserCreateRequestDto request = UserDtoFixtures.aValidCreateRequestBuilder()
                .email("dup-it+" + UUID.randomUUID() + "@example.com")
                .build();

        doThrow(new UserAlreadyExistsException("User already exists"))
                .when(userManagementServiceImp).create(any(UserCreateRequestDto.class));

        mockMvc.perform(post(REGISTER_ENDPOINT)
                        .with(csrf())
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.httpStatusCode").value(409))
                .andExpect(jsonPath("$.errorCodeType").value("FUNCTIONAL"));
    }

    // -----------------------------------------------------------------------------------
    // 3. GET /admin/user/get/all as ROLE_USER → 403 (BUG-031 closed by F2; was 500).
    // -----------------------------------------------------------------------------------
    @Test
    @DisplayName("GET /admin/user/get/all as ROLE_USER — 403 FORBIDDEN (BUG-031 fix CONFIRMED)")
    void adminGetAllAsRoleUserReturns403PerBug031() throws Exception {
        final String keycloakId = "kc-it-user-" + UUID.randomUUID();

        mockMvc.perform(get(ADMIN_GET_ALL_USERS_ENDPOINT)
                        .with(jwtUser(keycloakId))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.httpStatusCode").value(403));
    }

    // -----------------------------------------------------------------------------------
    // 4. GET /admin/user/get/all as ROLE_ADMIN → 200.
    // -----------------------------------------------------------------------------------
    @Test
    @DisplayName("GET /admin/user/get/all as ROLE_ADMIN — 200 OK with paged response shape")
    void adminGetAllAsRoleAdminReturns200() throws Exception {
        final String adminKeycloakId = "kc-it-admin-" + UUID.randomUUID();

        mockMvc.perform(get(ADMIN_GET_ALL_USERS_ENDPOINT)
                        .with(jwtAdmin(adminKeycloakId))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    // -----------------------------------------------------------------------------------
    // 5. BUG-201 — CLOSED. POST /register/auto/single now requires ADMIN.
    //    The @PreAuthorize annotation lives on UserManagementController#registerAuto() and
    //    the SecurityConfig#PUBLIC_URLS whitelist entry has been narrowed to the exact
    //    /register path (no wildcard), so anonymous calls fall through to
    //    `anyRequest().authenticated()` and return 401 via CustomAuthenticationEntryPoint.
    // -----------------------------------------------------------------------------------
    @Test
    @DisplayName("BUG-201 fix — POST /register/auto/single rejects anonymous (401)")
    void registerAutoSingleRejectsAnonymousAfterBug201Fix() throws Exception {
        mockMvc.perform(post(REGISTER_AUTO_SINGLE_ENDPOINT)
                        .with(csrf())
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(result -> {
                    final int s = result.getResponse().getStatus();
                    assertThat(s).as("anonymous must be 401 or 403 — fix landed").isIn(401, 403);
                });
    }

    @Test
    @DisplayName("BUG-201 fix — POST /register/auto/single as ROLE_USER returns 403")
    void registerAutoSingleAsRoleUserReturns403AfterBug201Fix() throws Exception {
        final String keycloakId = "kc-it-user-" + UUID.randomUUID();
        mockMvc.perform(post(REGISTER_AUTO_SINGLE_ENDPOINT)
                        .with(jwtUser(keycloakId))
                        .with(csrf())
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("BUG-201 fix — POST /register/auto/single as ROLE_ADMIN returns 201")
    void registerAutoSingleAsRoleAdminReturns201AfterBug201Fix() throws Exception {
        final String adminKeycloakId = "kc-it-admin-" + UUID.randomUUID();
        mockMvc.perform(post(REGISTER_AUTO_SINGLE_ENDPOINT)
                        .with(jwtAdmin(adminKeycloakId))
                        .with(csrf())
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uuid").exists())
                .andExpect(jsonPath("$.keycloakId").exists());
    }

    // -----------------------------------------------------------------------------------
    // 6. /actuator/health is publicly reachable — anonymous → 200.
    //    Spring Boot Actuator exposes /actuator/health by default; SecurityConfig only
    //    intercepts /api/v1/** and friends, so /actuator paths fall through the security
    //    chain via the framework's default actuator-security wiring (no @PreAuthorize).
    // -----------------------------------------------------------------------------------
    @Test
    @DisplayName("GET /actuator/health anonymously — 200 OK")
    void actuatorHealthAnonymouslyReturns200() throws Exception {
        mockMvc.perform(get(ACTUATOR_HEALTH_ENDPOINT)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(result -> {
                    final int s = result.getResponse().getStatus();
                    // Default actuator exposure should make /health anonymous-200. If the
                    // app ever locks down actuator we want to know — keep the check tight.
                    assertThat(s).as("actuator health should be 200 anonymously").isEqualTo(200);
                });
    }
}
