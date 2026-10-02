package com.novatech.cybertech.api.controllers.spec;

import com.novatech.cybertech.api.error.model.ErrorResponseDto;
import com.novatech.cybertech.dto.request.user.UserCreateRequestDto;
import com.novatech.cybertech.dto.request.user.UserSelfUpdateRequestDto;
import com.novatech.cybertech.dto.response.user.UserResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Map;
import java.util.UUID;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@Tag(name = "User", description = "User self-service: signup, read a profile by UUID and update one's own profile")
public interface UserControllerApiSpec {

    @Operation(summary = "Public signup",
            description = """
                    Anonymous endpoint (whitelisted in SecurityConfig#PUBLIC_URLS). Creates the Keycloak
                    account and the local user in one saga; the optional nested bank card is stored
                    encrypted. The response only carries id, email and username — the Keycloak id is
                    deliberately not exposed to an anonymous caller.
                    """,
            security = {},
            requestBody = @RequestBody(
                    description = "Signup payload",
                    required = true,
                    content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = UserCreateRequestDto.class))),
            responses = {
                    @ApiResponse(responseCode = "201", description = "User created — body: {id, email, username}", content = @Content(mediaType = APPLICATION_JSON_VALUE)),
                    @ApiResponse(responseCode = "400", description = "Invalid input data / Validation error", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "409", description = "A user with this email / username already exists", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
            })
    ResponseEntity<Map<?, ?>> register(final UserCreateRequestDto userCreateRequestDto);

    @Operation(summary = "Request a User by UUID",
            description = "Fetches a user's details based on their unique UUID. Non-admin callers may only read their own profile.",
            security = @SecurityRequirement(name = "keycloak"),
            parameters = {
                    @Parameter(name = "userUuid", description = "UUID for searching a user", required = true, schema = @Schema(implementation = UUID.class))
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "User found successfully", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = UserResponseDto.class))),
                    @ApiResponse(responseCode = "400", description = "Bad request (e.g., invalid UUID format)", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - JWT token missing or invalid", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "403", description = "Operation forbidden - non-admin caller fetching another user's profile", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "404", description = "User not found", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))})
    ResponseEntity<UserResponseDto> getUserByUuid(final UUID userUuid,
                                                  @Parameter(hidden = true) final Jwt jwt,
                                                  @Parameter(hidden = true) final Authentication authentication);

    @Operation(summary = "Self-service profile update for the authenticated user",
            description = """
                    Frontend-gap #3 — patches the authenticated user's own profile (first/last name,
                    phone number, address). The user UUID is resolved from the JWT subject so there
                    is no IDOR risk: callers can only mutate their own row. Email/username are managed
                    by Keycloak and intentionally NOT exposed here. Admin-only fields (status, role)
                    are also off-limits to self-service.
                    """,
            security = @SecurityRequirement(name = "keycloak"),
            requestBody = @RequestBody(
                    description = "Self-update payload — every field is optional; nulls leave the corresponding column untouched.",
                    required = true,
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserSelfUpdateRequestDto.class))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "User profile updated", content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserResponseDto.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid input data / Validation error", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - JWT token missing or invalid", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "403", description = "Operation forbidden", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "404", description = "User not found (JWT subject points to a stale Keycloak entry)", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponseDto.class)))
            })
    ResponseEntity<UserResponseDto> updateMe(final UserSelfUpdateRequestDto dto,
                                             @Parameter(hidden = true) final Jwt jwt);
}
