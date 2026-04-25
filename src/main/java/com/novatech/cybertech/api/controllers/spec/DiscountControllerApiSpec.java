package com.novatech.cybertech.api.controllers.spec;

import com.novatech.cybertech.api.error.model.ErrorResponseDto;
import com.novatech.cybertech.dto.data.DiscountContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * Public-facing read-only contract for the discount/campaign endpoints. The matching
 * implementation lives in {@code com.novatech.cybertech.api.controllers.implementation.DiscountController}
 * and is mounted under {@code /api/v1/services/discounts}, whitelisted in
 * {@code SecurityConfig#PUBLIC_URLS} so anonymous storefront browsing can render the
 * banner / discount picker without an auth token.
 */
@Tag(name = "DiscountController", description = "Public discount/campaign read endpoints")
public interface DiscountControllerApiSpec {

    @Operation(
            summary = "List currently-active discount campaigns (public)",
            description = """
                    Returns every discount campaign that is `enabled = true` AND inside its
                    `[startsAt, endsAt]` window at request time. Used by the storefront home page
                    banner and by the checkout discount picker.

                    Public endpoint — no JWT required. Whitelisted in SecurityConfig#PUBLIC_URLS.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Active campaigns retrieved successfully (may be an empty list when no campaign is currently active). Public — no auth required.",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE, array = @ArraySchema(schema = @Schema(implementation = DiscountContext.class)))),
            @ApiResponse(responseCode = "500", description = "Internal server error while loading discount campaigns",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    ResponseEntity<List<DiscountContext>> getActiveCampaigns();
}
