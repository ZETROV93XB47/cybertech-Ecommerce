package com.novatech.cybertech.api.controllers.spec;

import com.novatech.cybertech.api.error.model.ErrorResponseDto;
import com.novatech.cybertech.dto.request.user.BankCardCreationRequestDto;
import com.novatech.cybertech.dto.request.user.BankCardUpdateRequestDto;
import com.novatech.cybertech.dto.response.user.BankCardResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.UUID;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@Tag(name = "Bank Card", description = "Endpoints to manage the authenticated user's bank card")
public interface BankCardControllerApiSpec {

    // --- Endpoints Sécurisés (User Context) ---

    @Operation(summary = "Add a bank card to current user",
            description = "Adds a new bank card for the authenticated user. User can only have one card.",
            security = @SecurityRequirement(name = "keycloak"),
            requestBody = @RequestBody(content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = BankCardCreationRequestDto.class))),
            responses = {
                    @ApiResponse(responseCode = "201", description = "Bank card added successfully", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = BankCardResponseDto.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "409", description = "User already has a bank card", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
            })
    ResponseEntity<BankCardResponseDto> addBankCard(BankCardCreationRequestDto dto, Jwt jwt);

    @Operation(summary = "Delete current user's bank card",
            description = "Deletes the bank card associated with the authenticated user.",
            security = @SecurityRequirement(name = "keycloak"),
            responses = {
                    @ApiResponse(responseCode = "204", description = "Bank card deleted successfully"),
                    @ApiResponse(responseCode = "404", description = "Bank card not found", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
            })
    ResponseEntity<Void> deleteBankCard(Jwt jwt);

    @Operation(summary = "Update current user's bank card",
            description = "Updates the bank card details for the authenticated user.",
            security = @SecurityRequirement(name = "keycloak"),
            requestBody = @RequestBody(content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = BankCardUpdateRequestDto.class))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Bank card updated successfully", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = BankCardResponseDto.class))),
                    @ApiResponse(responseCode = "404", description = "Bank card not found", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
            })
    ResponseEntity<BankCardResponseDto> updateBankCard(BankCardUpdateRequestDto dto, Jwt jwt);

    // --- BUG-038 — default-card surface -----------------------------------------------

    @Operation(summary = "Set a bank card as the caller's default",
            description = "Marks the specified card as the authenticated user's default card, clearing any previous default. Enforces ownership via the JWT subject.",
            security = @SecurityRequirement(name = "keycloak"),
            parameters = {@Parameter(name = "cardUuid", description = "UUID of the card to promote to default")},
            responses = {
                    @ApiResponse(responseCode = "204", description = "Default card updated"),
                    @ApiResponse(responseCode = "403", description = "Caller does not own this card", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "404", description = "Bank card not found", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
            })
    ResponseEntity<Void> setDefaultBankCard(UUID cardUuid, Jwt jwt);

    @Operation(summary = "Get the caller's default bank card",
            description = "Returns the authenticated user's default card as a masked response DTO (no PAN is exposed).",
            security = @SecurityRequirement(name = "keycloak"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Default card", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = BankCardResponseDto.class))),
                    @ApiResponse(responseCode = "403", description = "No default bank card set", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
            })
    ResponseEntity<BankCardResponseDto> getDefaultBankCard(Jwt jwt);

    @Operation(summary = "List the authenticated user's bank cards",
            description = """
                    Frontend-gap #4 — returns every bank card owned by the authenticated caller as a
                    list of PCI-masked DTOs. The current domain model enforces 1-card-per-user
                    (UserEntity.bankCardEntity is @OneToOne) so the list contains at most one entry,
                    but the list shape keeps the contract forward-compatible should that constraint
                    be relaxed.
                    """,
            security = @SecurityRequirement(name = "keycloak"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "List of cards (possibly empty)", content = @Content(mediaType = APPLICATION_JSON_VALUE, array = @ArraySchema(schema = @Schema(implementation = BankCardResponseDto.class)))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "403", description = "Operation forbidden", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
            })
    ResponseEntity<List<BankCardResponseDto>> getAllMine(Jwt jwt);
}