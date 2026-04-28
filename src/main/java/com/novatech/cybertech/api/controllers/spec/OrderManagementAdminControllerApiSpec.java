package com.novatech.cybertech.api.controllers.spec;

import com.novatech.cybertech.api.error.model.ErrorResponseDto;
import com.novatech.cybertech.dto.response.order.OrderResponseDto;
import com.novatech.cybertech.entities.enums.OrderStatus;
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

import java.util.Set;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * Frontend-gap #2 — OpenAPI surface for the admin-side paginated order listing.
 *
 * <p>Mirrors the {@link UserManagementAdminApiSpec} convention: this controller is
 * ADMIN-only at the implementation layer (class-level {@code @PreAuthorize}), the spec
 * methods don't repeat the role check.</p>
 */
@Tag(name = "Order Admin", description = "Admin-only endpoints for paginated order listing with optional status / user filters")
public interface OrderManagementAdminControllerApiSpec {

    @Operation(summary = "Get all Orders paginated (Admin)",
            description = """
                    Paginated listing of every order in the system with optional filters on status
                    and user keycloakId. Both filters are nullable — omitting them returns every
                    order regardless of state or owner.
                    """,
            security = @SecurityRequirement(name = "keycloak"),
            parameters = {
                    @Parameter(name = "status", description = "Optional set of OrderStatus values to keep — repeat the query param to pass multiple values"),
                    @Parameter(name = "userKeycloakId", description = "Optional Keycloak subject id of the user to filter by"),
                    @Parameter(name = "page", description = "0-based page index (default 0)"),
                    @Parameter(name = "size", description = "page size (default 20)")
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "Page of orders", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = Page.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - JWT token missing or invalid", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "403", description = "Operation forbidden - ADMIN role required", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
            })
    ResponseEntity<Page<OrderResponseDto>> getAllOrders(
            @Parameter(description = "Optional set of OrderStatus values to keep") final Set<OrderStatus> status,
            @Parameter(description = "Optional user keycloakId to filter by") final String userKeycloakId,
            @Parameter(hidden = true) final Pageable pageable
    );
}
