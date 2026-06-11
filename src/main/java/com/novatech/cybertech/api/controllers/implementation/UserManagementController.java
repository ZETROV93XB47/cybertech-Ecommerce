package com.novatech.cybertech.api.controllers.implementation;

import com.novatech.cybertech.api.controllers.spec.UserControllerApiSpec;
import com.novatech.cybertech.dto.request.user.UserCreateRequestDto;
import com.novatech.cybertech.dto.request.user.UserSelfUpdateRequestDto;
import com.novatech.cybertech.dto.response.user.UserResponseDto;
import com.novatech.cybertech.services.core.UserManagementService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

import static com.novatech.cybertech.constants.CyberTechAppConstants.APP_API_VERSION;
import static com.novatech.cybertech.constants.CyberTechAppConstants.RESPONSE_KEY_EMAIL;
import static com.novatech.cybertech.constants.CyberTechAppConstants.RESPONSE_KEY_ID;
import static com.novatech.cybertech.constants.CyberTechAppConstants.RESPONSE_KEY_USERNAME;
import static com.novatech.cybertech.constants.CyberTechAppConstants.USER_CRUD_CONTROLLER_BASE_PATH;
import static com.novatech.cybertech.utils.ControllerSecurityUtils.isAdmin;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.ResponseEntity.ok;

/**
 * User-facing user-management controller.
 *
 * <p>Hosts the human signup endpoint ({@code POST /register}), user lookup, self-update,
 * and a liveness check.</p>
 *
 * <p>Security contract:
 * <ul>
 *   <li>{@code POST /register} — public (anonymous signup).</li>
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

    // FIX(INTERFACE-CONTRACT): inject service interface instead of concrete impl per project convention
    private final UserManagementService userManagementServiceImp;

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
     * attacker probing signup must not be able to harvest.</p>
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

    /**
     * Frontend-gap #3 — self-service profile update. Resolves the caller via the JWT subject
     * and delegates to {@link UserManagementService#updateMe(String, UserSelfUpdateRequestDto)};
     * no UUID is accepted from the body, ruling out IDOR by construction.
     */
    @Override
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    @PatchMapping(value = "/me", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<UserResponseDto> updateMe(@Valid @RequestBody final UserSelfUpdateRequestDto dto,
                                                    @AuthenticationPrincipal final Jwt jwt) {
        return ResponseEntity.ok(userManagementServiceImp.updateMe(jwt.getSubject(), dto));
    }
}
