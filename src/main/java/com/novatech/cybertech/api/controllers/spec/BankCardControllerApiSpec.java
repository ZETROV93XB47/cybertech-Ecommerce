package com.novatech.cybertech.api.controllers.spec;

import com.novatech.cybertech.api.error.model.ErrorResponseDto;
import com.novatech.cybertech.dto.request.user.BankCardCreationRequestDto;
import com.novatech.cybertech.dto.request.user.BankCardUpdateRequestDto;
import com.novatech.cybertech.dto.response.user.BankCardResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;


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
                    @ApiResponse(responseCode = "409", description = "User already has a bank card (BANK_CARD_ALREADY_EXISTS); use PUT /bank-card/replace", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
            })
    ResponseEntity<BankCardResponseDto> addBankCard(BankCardCreationRequestDto dto, Jwt jwt);

    @Operation(summary = "Replace current user's bank card",
            description = """
                    Replaces the authenticated user's bank card with a new one (same body as /add), or adds it
                    when the user has none. Delete and add run in a single transaction: the new card is
                    validated first and any failure keeps the current card, so the account is never left
                    without one.
                    """,
            security = @SecurityRequirement(name = "keycloak"),
            requestBody = @RequestBody(content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = BankCardCreationRequestDto.class))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Bank card replaced (or added)", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = BankCardResponseDto.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid input or expired card (BANK_CARD_EXPIRED); the current card is kept", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
            })
    ResponseEntity<BankCardResponseDto> replaceBankCard(BankCardCreationRequestDto dto, Jwt jwt);

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

    // --- single-card surface -------------------------------------------------------------

    @Operation(summary = "Get the caller's bank card",
            description = "Returns the authenticated user's bank card as a masked response DTO (no PAN is exposed).",
            security = @SecurityRequirement(name = "keycloak"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Bank card", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = BankCardResponseDto.class))),
                    @ApiResponse(responseCode = "404", description = "The caller has no bank card", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
            })
    ResponseEntity<BankCardResponseDto> getBankCard(Jwt jwt);
}