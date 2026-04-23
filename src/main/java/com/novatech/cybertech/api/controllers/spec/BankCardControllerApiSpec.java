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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

public interface BankCardControllerApiSpec {

    // --- Endpoints Sécurisés (User Context) ---

    @Operation(summary = "Add a bank card to current user",
            description = "Adds a new bank card for the authenticated user. User can only have one card.",
            security = @SecurityRequirement(name = "bearerAuth"),
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
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "204", description = "Bank card deleted successfully"),
                    @ApiResponse(responseCode = "404", description = "Bank card not found", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
            })
    ResponseEntity<Void> deleteBankCard(Jwt jwt);

    @Operation(summary = "Update current user's bank card",
            description = "Updates the bank card details for the authenticated user.",
            security = @SecurityRequirement(name = "bearerAuth"),
            requestBody = @RequestBody(content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = BankCardUpdateRequestDto.class))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Bank card updated successfully", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = BankCardResponseDto.class))),
                    @ApiResponse(responseCode = "404", description = "Bank card not found", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
            })
    ResponseEntity<BankCardResponseDto> updateBankCard(BankCardUpdateRequestDto dto, Jwt jwt);

    // --- Endpoints CRUD Basiques (Non sécurisés comme demandé, ou Admin) ---

    @Operation(summary = "Get all bank cards",
            description = "Retrieves all bank cards in the system.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "List of bank cards", content = @Content(mediaType = APPLICATION_JSON_VALUE, array = @ArraySchema(schema = @Schema(implementation = BankCardResponseDto.class))))
            })
    ResponseEntity<Page<BankCardResponseDto>> getAllBankCards(final Pageable pageable);

    @Operation(summary = "Get bank card by UUID",
            description = "Retrieves a specific bank card by its UUID.",
            parameters = {@Parameter(name = "uuid", description = "UUID of the bank card")},
            responses = {
                    @ApiResponse(responseCode = "200", description = "Bank card found", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = BankCardResponseDto.class))),
                    @ApiResponse(responseCode = "404", description = "Bank card not found", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
            })
    ResponseEntity<BankCardResponseDto> getBankCardByUuid(UUID uuid);

    @Operation(summary = "Create a bank card (Admin)",
            description = "Creates a bank card directly linked to a user UUID.",
            requestBody = @RequestBody(content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = BankCardCreationRequestDto.class))),
            responses = {
                    @ApiResponse(responseCode = "201", description = "Bank card created", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = BankCardResponseDto.class)))
            })
    ResponseEntity<BankCardResponseDto> createBankCard(BankCardCreationRequestDto dto);

    @Operation(summary = "Update a bank card (Admin)",
            description = "Updates a bank card by UUID.",
            requestBody = @RequestBody(content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = BankCardUpdateRequestDto.class))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Bank card updated", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = BankCardResponseDto.class)))
            })
    ResponseEntity<BankCardResponseDto> updateBankCardAdmin(BankCardUpdateRequestDto dto);

    @Operation(summary = "Delete a bank card by UUID",
            description = "Deletes a bank card by its UUID.",
            parameters = {@Parameter(name = "uuid", description = "UUID of the bank card")},
            responses = {
                    @ApiResponse(responseCode = "204", description = "Bank card deleted")
            })
    ResponseEntity<Void> deleteBankCardByUuid(UUID uuid);

    // --- BUG-038 — default-card surface -----------------------------------------------

    @Operation(summary = "Set a bank card as the caller's default",
            description = "Marks the specified card as the authenticated user's default card, clearing any previous default. Enforces ownership via the JWT subject.",
            security = @SecurityRequirement(name = "bearerAuth"),
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
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Default card", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = BankCardResponseDto.class))),
                    @ApiResponse(responseCode = "403", description = "No default bank card set", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
            })
    ResponseEntity<BankCardResponseDto> getDefaultBankCard(Jwt jwt);
}