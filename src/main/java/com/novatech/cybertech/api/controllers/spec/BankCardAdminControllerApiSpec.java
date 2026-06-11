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
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@Tag(name = "Bank Card Admin", description = "Admin-only endpoints for bank card CRUD")
public interface BankCardAdminControllerApiSpec {

    @Operation(summary = "Get all bank cards (Admin)",
            description = "Retrieves every bank card in the system (paginated).",
            security = @SecurityRequirement(name = "keycloak"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Page of bank cards",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, array = @ArraySchema(schema = @Schema(implementation = BankCardResponseDto.class)))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - JWT token missing or invalid",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "403", description = "Operation forbidden - ADMIN role required",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
            })
    ResponseEntity<Page<BankCardResponseDto>> getAllBankCards(final Pageable pageable);

    @Operation(summary = "Get bank card by UUID (Admin)",
            description = "Retrieves a specific bank card by its UUID.",
            security = @SecurityRequirement(name = "keycloak"),
            parameters = {@Parameter(name = "uuid", description = "UUID of the bank card", required = true, schema = @Schema(implementation = UUID.class))},
            responses = {
                    @ApiResponse(responseCode = "200", description = "Bank card found",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = BankCardResponseDto.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - JWT token missing or invalid",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "403", description = "Operation forbidden - ADMIN role required",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "404", description = "Bank card not found",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
            })
    ResponseEntity<BankCardResponseDto> getBankCardByUuid(UUID uuid);

    @Operation(summary = "Create a bank card (Admin)",
            description = "Creates a bank card directly linked to a user UUID.",
            security = @SecurityRequirement(name = "keycloak"),
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = BankCardCreationRequestDto.class))),
            responses = {
                    @ApiResponse(responseCode = "201", description = "Bank card created",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = BankCardResponseDto.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid input data / Validation error",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "403", description = "Operation forbidden - ADMIN role required",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
            })
    ResponseEntity<BankCardResponseDto> createBankCard(BankCardCreationRequestDto dto);

    @Operation(summary = "Update a bank card (Admin)",
            description = "Updates a bank card by UUID (UUID carried in the request body).",
            security = @SecurityRequirement(name = "keycloak"),
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = BankCardUpdateRequestDto.class))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Bank card updated",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = BankCardResponseDto.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - JWT token missing or invalid",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "403", description = "Operation forbidden - ADMIN role required",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "404", description = "Bank card not found",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
            })
    ResponseEntity<BankCardResponseDto> updateBankCardAdmin(BankCardUpdateRequestDto dto);

    @Operation(summary = "Delete a bank card by UUID (Admin)",
            description = "Deletes a bank card by its UUID.",
            security = @SecurityRequirement(name = "keycloak"),
            parameters = {@Parameter(name = "uuid", description = "UUID of the bank card", required = true, schema = @Schema(implementation = UUID.class))},
            responses = {
                    @ApiResponse(responseCode = "204", description = "Bank card deleted"),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - JWT token missing or invalid",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "403", description = "Operation forbidden - ADMIN role required",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "404", description = "Bank card not found",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
            })
    ResponseEntity<Void> deleteBankCardByUuid(UUID uuid);
}
