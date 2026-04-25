package com.novatech.cybertech.api.controllers.spec;

import com.novatech.cybertech.api.error.model.ErrorResponseDto;
import com.novatech.cybertech.dto.request.admin.DiscountCampaignUpdateRequestDto;
import com.novatech.cybertech.dto.response.admin.DiscountCampaignResponseDto;
import com.novatech.cybertech.entities.enums.DiscountType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * Admin-only contract for managing discount campaigns. Mutations flush the runtime
 * cache so the next price calculation picks up the new state without redeploy. The
 * matching implementation lives in
 * {@code com.novatech.cybertech.api.controllers.implementation.DiscountAdminController}
 * and is mounted under {@code /api/v1/services/admin/discounts}. Every endpoint is
 * guarded server-side by {@code @PreAuthorize("hasRole('ADMIN')")}.
 */
@Tag(name = "DiscountAdminController", description = "Admin discount/campaign management endpoints")
public interface DiscountAdminControllerApiSpec {

    @Operation(
            summary = "List all discount campaigns (Admin)",
            description = """
                    Returns every discount campaign in the system, regardless of `enabled` flag
                    or active window. Used by the back-office to render the campaign management
                    table.

                    Requires a valid JWT in the Authorization header AND the `ADMIN` realm role.
                    """,
            security = @SecurityRequirement(name = "keycloak")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "All discount campaigns retrieved successfully",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE, array = @ArraySchema(schema = @Schema(implementation = DiscountCampaignResponseDto.class)))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - JWT token missing or invalid",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - ADMIN role required",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error while loading discount campaigns",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    ResponseEntity<List<DiscountCampaignResponseDto>> getAll();

    @Operation(
            summary = "Get a discount campaign by type (Admin)",
            description = """
                    Fetches a single discount campaign identified by its `DiscountType` enum value
                    (e.g. `BLACK_FRIDAY`, `PERCENTAGE`, `BUY_ONE_GET_ONE_FREE`).

                    Requires a valid JWT in the Authorization header AND the `ADMIN` realm role.
                    """,
            security = @SecurityRequirement(name = "keycloak"),
            parameters = {
                    @Parameter(name = "discountType", description = "The DiscountType enum value identifying the campaign to retrieve", required = true, schema = @Schema(implementation = DiscountType.class))
            }
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Discount campaign retrieved successfully",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = DiscountCampaignResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Invalid DiscountType value",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - JWT token missing or invalid",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - ADMIN role required",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Discount campaign not found for the supplied DiscountType",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    ResponseEntity<DiscountCampaignResponseDto> getByDiscountType(final DiscountType discountType);

    @Operation(
            summary = "Update a discount campaign by type (Admin)",
            description = """
                    Patches the campaign identified by `discountType`. Only the fields supplied in
                    the request body are updated. The runtime discount cache is flushed after a
                    successful PATCH so the next price calculation reflects the new state.

                    Requires a valid JWT in the Authorization header AND the `ADMIN` realm role.
                    """,
            security = @SecurityRequirement(name = "keycloak"),
            parameters = {
                    @Parameter(name = "discountType", description = "The DiscountType enum value identifying the campaign to update", required = true, schema = @Schema(implementation = DiscountType.class))
            },
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Partial update payload for the targeted discount campaign. Only fields explicitly provided are updated.",
                    required = true,
                    content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = DiscountCampaignUpdateRequestDto.class))
            )
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Discount campaign updated successfully",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = DiscountCampaignResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input data / Validation error / Invalid DiscountType",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - JWT token missing or invalid",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - ADMIN role required",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Discount campaign not found for the supplied DiscountType",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error during update",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    ResponseEntity<DiscountCampaignResponseDto> update(final DiscountType discountType, final DiscountCampaignUpdateRequestDto request);
}
