package com.novatech.cybertech.api.controllers.implementation;

import com.novatech.cybertech.api.controllers.spec.UserControllerApiSpec;
import com.novatech.cybertech.dto.request.user.UserCreateRequestDto;
import com.novatech.cybertech.dto.response.user.UserResponseDto;
import com.novatech.cybertech.services.implementation.UserManagementServiceImp;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

import static com.novatech.cybertech.constants.CyberTechAppConstants.APP_API_VERSION;
import static com.novatech.cybertech.constants.CyberTechAppConstants.USER_CRUD_CONTROLLER_BASE_PATH;
import static com.novatech.cybertech.utils.ControllerSecurityUtils.isAdmin;
import static com.novatech.cybertech.utils.DataGenerator.generateUserCreateRequestDto;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.ResponseEntity.ok;

/**
 * User-facing user-management controller.
 *
 * <p>Hosts the human signup endpoint ({@code POST /register}) plus a developer-utility
 * endpoint ({@code POST /register/auto/single}) that mints a synthetic user with random
 * data via {@link com.novatech.cybertech.utils.DataGenerator}. The auto-register endpoint
 * is restricted to ADMIN callers (BUG-201) because it forges Keycloak + DB users without
 * any client-supplied data.</p>
 *
 * <p>Security contract:
 * <ul>
 *   <li>{@code POST /register} — public (anonymous signup).</li>
 *   <li>{@code POST /register/auto/single} — ADMIN only, enforced by
 *       {@code @PreAuthorize("hasRole('ADMIN')")}; the path is also no longer covered by the
 *       {@code SecurityConfig#PUBLIC_URLS} whitelist (only the exact {@code /register} path is).</li>
 *   <li>{@code GET /get/{userUuid}} and {@code GET /ok} — authenticated users only (any role).</li>
 * </ul></p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(version = APP_API_VERSION, value = USER_CRUD_CONTROLLER_BASE_PATH)
@Tag(name = "UserController", description = "API for user management")
public class UserManagementController implements UserControllerApiSpec {

    private static final String ACCESS_DENIED_OWN_PROFILE_ONLY = "Access denied: user can only fetch their own profile";

    // Response payload keys returned by /register and /register/auto/single. Extracted as
    // constants so the JSON contract surfaces in one place — adding a field is a one-line
    // change and rename refactors no longer rely on string-search across the file.
    private static final String RESPONSE_KEY_ID = "id";
    private static final String RESPONSE_KEY_KEYCLOAK_ID = "keycloakId";
    private static final String RESPONSE_KEY_EMAIL = "email";
    private static final String RESPONSE_KEY_USERNAME = "username";

    private final UserManagementServiceImp userManagementServiceImp;

    /**
     * Returns a user by UUID. Authenticated users only — not in the public whitelist.
     *
     * <p><b>BUG-IDOR-D2:</b> regular USERs may only fetch their own profile; ADMINs bypass the
     * ownership check. Ownership is asserted by comparing the loaded entity's
     * {@code keycloakId} against the JWT {@code sub} claim. Differing identities for non-admins
     * trigger {@link AccessDeniedException} (→ 403 via the existing handler).</p>
     *
     * @param userUuid the unique identifier of the user to fetch.
     * @param jwt      caller identity.
     * @return the {@link UserResponseDto} when found; throws
     *         {@link com.novatech.cybertech.exceptions.UserNotFoundException} (mapped to
     *         HTTP 404 by the controller advice) otherwise.
     */
    @Override
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    @GetMapping(value = "/get/{userUuid}", produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<UserResponseDto> getUserByUuid(@PathVariable("userUuid") final UUID userUuid,
                                                          @AuthenticationPrincipal final Jwt jwt,
                                                          final Authentication authentication) {
        final UserResponseDto loadedUser = userManagementServiceImp.getByUUID(userUuid);

        if (!isAdmin(authentication) && !loadedUser.getKeycloakId().equals(jwt.getSubject())) {
            throw new AccessDeniedException(ACCESS_DENIED_OWN_PROFILE_ONLY);
        }

        return ResponseEntity.status(HttpStatus.OK).body(loadedUser);
    }

    /**
     * Anonymous, public human-signup endpoint.
     *
     * <p>Whitelisted in {@link com.novatech.cybertech.config.SecurityConfig#PUBLIC_URLS} via the
     * exact path {@code /api/v1/services/user/register} (no wildcard), so anonymous calls
     * succeed. The body is validated via {@link Valid}; on success a Keycloak user and a
     * local {@code UserEntity} are persisted in a single transaction.</p>
     *
     * @param userCreateRequestDto the human-supplied signup payload.
     * @return HTTP 201 with a compact {@code Map} body containing {@code id} (local UUID),
     *         {@code email} and {@code username}.
     *
     * <p><b>BUG-LEAK-D6 (this fix):</b> the {@code keycloakId} previously included in this
     * anonymous-public response is now stripped — it is an identity-system internal that an
     * attacker probing signup must not be able to harvest. The admin-only
     * {@link #registerAuto()} endpoint still exposes it because the synthetic-user use case
     * legitimately needs the handle.</p>
     */
    @PostMapping("/register")
    public ResponseEntity<Map<?, ?>> register(@Valid @RequestBody UserCreateRequestDto userCreateRequestDto) {
        final UserResponseDto created = userManagementServiceImp.create(userCreateRequestDto);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                RESPONSE_KEY_ID, created.getUuid(),
                RESPONSE_KEY_EMAIL, created.getEmail(),
                RESPONSE_KEY_USERNAME, created.getUsername()
        ));
    }

    /**
     * Developer / load-test utility — mints a synthetic user via
     * {@link com.novatech.cybertech.utils.DataGenerator}.
     *
     * <p><b>Security (BUG-201):</b> ADMIN-only. The endpoint forges a fully-formed user
     * (Keycloak + DB) without any client-supplied identity, so leaving it anonymous would
     * let an attacker pollute the user table and Keycloak realm. The
     * {@code @PreAuthorize("hasRole('ADMIN')")} below is the primary guard; the
     * {@code SecurityConfig#PUBLIC_URLS} entry that previously matched
     * {@code /api/v1/services/user/register/**} has been narrowed to the exact
     * {@code /register} path so this URL no longer falls into {@code permitAll()}.</p>
     *
     * <p>Wave 3 regression-fix: returns a {@code Map.of(...)} body (mirrors the
     * {@link #register(UserCreateRequestDto)} pattern) so the synthetic user's
     * {@code keycloakId} is exposed to the admin caller. Returning a {@link UserResponseDto}
     * directly suppressed {@code keycloakId} via the {@code @JsonIgnore} on the DTO field —
     * the admin caller cannot identify the synthetic user without it.</p>
     *
     * @return HTTP 201 with a compact {@code Map} body containing {@code id} (local UUID),
     *         {@code keycloakId}, {@code email} and {@code username}.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/register/auto/single")
    public ResponseEntity<Map<String, Object>> registerAuto() {
        final UserResponseDto created = userManagementServiceImp.create(generateUserCreateRequestDto());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                RESPONSE_KEY_ID, created.getUuid(),
                RESPONSE_KEY_KEYCLOAK_ID, created.getKeycloakId(),
                RESPONSE_KEY_EMAIL, created.getEmail(),
                RESPONSE_KEY_USERNAME, created.getUsername()
        ));
    }


    /**
     * Lightweight liveness ping. Authenticated users only (not whitelisted in
     * {@code SecurityConfig#PUBLIC_URLS}). Useful for debugging the JWT auth chain
     * without hitting any service.
     */
    @Operation(summary = "Health check endpoint",
            description = "A simple endpoint to check if the UserController is responsive.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Service is up and running",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(type = "string", example = "Hello Guys !!!")))
            })
    @GetMapping(value = "/ok", produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<String> healthCheck() {
        return ok("Hello Guys !!! 😁🔥🔥🔥");
    }
}
