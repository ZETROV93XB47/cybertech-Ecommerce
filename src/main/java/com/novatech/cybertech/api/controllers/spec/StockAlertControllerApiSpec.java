package com.novatech.cybertech.api.controllers.spec;

import com.novatech.cybertech.api.error.model.ErrorResponseDto;
import com.novatech.cybertech.dto.response.stockalert.StockAlertResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@Tag(name = "Stock alerts", description = "Back-in-stock alerts of the connected user")
public interface StockAlertControllerApiSpec {

    @Operation(summary = "Alert me when this product is back in stock",
            description = "Creates a back-in-stock alert for an out-of-stock product (available = stock - reserved = 0). "
                    + "Checked every 10 minutes: once the product can be ordered again, the user gets an e-mail plus a "
                    + "Web Push on each subscribed browser, and the alert is over (subscribe again for the next time). "
                    + "Every alerted user is notified, even when fewer units came back. An alert never triggered "
                    + "expires after 90 days.",
            security = @SecurityRequirement(name = "keycloak"),
            responses = {
                    @ApiResponse(responseCode = "201", description = "Alert pending",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = StockAlertResponseDto.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - User not logged in",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "404", description = "PRODUCT_NOT_FOUND / USER_NOT_FOUND",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "409", description = "PRODUCT_ALREADY_IN_STOCK - the product can already be ordered; "
                            + "STOCK_ALERT_ALREADY_EXISTS - an alert is already pending for this product",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "500", description = "Internal Server Error",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
            })
    ResponseEntity<StockAlertResponseDto> subscribe(
            @Parameter(description = "UUID of the out-of-stock product") UUID productUuid,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt);

    @Operation(summary = "Remove my back-in-stock alert",
            description = "Cancels the connected user's pending alert for the product. Idempotent: 204 even without a pending alert.",
            security = @SecurityRequirement(name = "keycloak"),
            responses = {
                    @ApiResponse(responseCode = "204", description = "No pending alert left for this product"),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - User not logged in",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "500", description = "Internal Server Error",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
            })
    ResponseEntity<Void> unsubscribe(
            @Parameter(description = "UUID of the product") UUID productUuid,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt);

    @Operation(summary = "My pending back-in-stock alerts (paginated)",
            description = "One page of the connected user's pending alerts, as a PagedModel ({content, page}). "
                    + "Defaults: page=0, size=20, sort=requestedAt,desc.",
            security = @SecurityRequirement(name = "keycloak"),
            parameters = {
                    @Parameter(name = "page", description = "0-based page index (default 0)", required = false),
                    @Parameter(name = "size", description = "Page size (default 20, max 100)", required = false),
                    @Parameter(name = "sort", description = "property,direction (default requestedAt,desc)", required = false)
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "One page of pending alerts (possibly empty)"),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - User not logged in",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "500", description = "Internal Server Error",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
            })
    ResponseEntity<Page<StockAlertResponseDto>> getMine(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Parameter(hidden = true) Pageable pageable);
}
