package com.novatech.cybertech.api.controllers.spec;

import com.novatech.cybertech.api.error.model.ErrorResponseDto;
import com.novatech.cybertech.dto.request.user.BankCardCreationRequestDto;
import com.novatech.cybertech.dto.request.user.BankCardUpdateRequestDto;
import com.novatech.cybertech.dto.response.user.BankCardResponseDto;
import com.novatech.cybertech.dto.response.user.BankCardSetupIntentResponseDto;
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

    @Operation(summary = "Prepare a card registration (Stripe SetupIntent)",
            description = """
                    Step 1 of saving a card. Creates the caller's Stripe Customer on first use, then an
                    off-session card SetupIntent. The frontend initialises Stripe.js with `publishableKey`,
                    confirms the SetupIntent with `clientSecret` in Stripe Elements (the card number never
                    reaches the backend), then calls POST /bank-card/add with `setupIntentId`.
                    """,
            security = @SecurityRequirement(name = "keycloak"),
            responses = {
                    @ApiResponse(responseCode = "201", description = "SetupIntent created", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = BankCardSetupIntentResponseDto.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "500", description = "Stripe unavailable (PAYMENT_PROCESSING_ERROR)", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "503", description = "stripe.publishable-key not configured (PAYMENT_PROVIDER_NOT_CONFIGURED)", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
            })
    ResponseEntity<BankCardSetupIntentResponseDto> createSetupIntent(Jwt jwt);

    @Operation(summary = "Save the card confirmed in Stripe Elements",
            description = """
                    Step 2 of saving a card. The SetupIntent must belong to the caller's Stripe Customer and be
                    `succeeded`. The card becomes the user's only card and the Customer's default payment method;
                    it replaces a previous card (detached at Stripe). Every payment is then charged on it.
                    Only brand, last four digits, expiry and holder name are stored. A body with a card number
                    (former contract) is rejected with a 400.
                    """,
            security = @SecurityRequirement(name = "keycloak"),
            requestBody = @RequestBody(content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = BankCardCreationRequestDto.class))),
            responses = {
                    @ApiResponse(responseCode = "201", description = "Card saved", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = BankCardResponseDto.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid body, unknown or unfinished SetupIntent (INVALID_CARD_SETUP), expired card (BANK_CARD_EXPIRED)", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "403", description = "SetupIntent of another user (ACCESS_DENIED)", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
            })
    ResponseEntity<BankCardResponseDto> addBankCard(BankCardCreationRequestDto dto, Jwt jwt);

    @Operation(summary = "Delete current user's bank card",
            description = "Deletes the bank card associated with the authenticated user and detaches it from the Stripe Customer.",
            security = @SecurityRequirement(name = "keycloak"),
            responses = {
                    @ApiResponse(responseCode = "204", description = "Bank card deleted successfully"),
                    @ApiResponse(responseCode = "404", description = "Bank card not found", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
            })
    ResponseEntity<Void> deleteBankCard(Jwt jwt);

    @Operation(summary = "Update current user's bank card",
            description = "Updates the holder name and expiry date of the caller's card, at Stripe (PaymentMethod) and locally. The card number cannot change: save a new card instead.",
            security = @SecurityRequirement(name = "keycloak"),
            requestBody = @RequestBody(content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = BankCardUpdateRequestDto.class))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Bank card updated successfully", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = BankCardResponseDto.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid input, expired date (BANK_CARD_EXPIRED) or change refused by Stripe (INVALID_CARD_SETUP)", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "404", description = "Bank card not found", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
            })
    ResponseEntity<BankCardResponseDto> updateBankCard(BankCardUpdateRequestDto dto, Jwt jwt);

    // --- single-card surface -------------------------------------------------------------

    @Operation(summary = "Get the caller's bank card",
            description = "Returns the authenticated user's bank card as a masked response DTO (only the last four digits). A card saved before the Stripe flow cannot be charged and is reported as absent (404).",
            security = @SecurityRequirement(name = "keycloak"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Bank card", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = BankCardResponseDto.class))),
                    @ApiResponse(responseCode = "404", description = "The caller has no bank card", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
            })
    ResponseEntity<BankCardResponseDto> getBankCard(Jwt jwt);
}