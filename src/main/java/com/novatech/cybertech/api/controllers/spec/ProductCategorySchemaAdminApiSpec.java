package com.novatech.cybertech.api.controllers.spec;

import com.novatech.cybertech.api.error.model.ErrorResponseDto;
import com.novatech.cybertech.dto.request.product.ProductCategorySchemaCreateRequestDto;
import com.novatech.cybertech.dto.request.product.ProductCategorySchemaUpdateRequestDto;
import com.novatech.cybertech.dto.response.product.ProductCategorySchemaResponseDto;
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
 * Admin-only contract for the product-category-schema registry. Adding a new product category —
 * or changing the validation rules of an existing one — is a call to this API, not a Java code
 * change: {@code ProductValidationService} reads the registered JSON Schema at product-creation
 * time via a Caffeine cache kept in sync across instances by Redis Pub/Sub. Every endpoint is
 * guarded server-side by {@code @PreAuthorize("hasRole('ADMIN')")}.
 */
@Tag(name = "ProductCategorySchemaAdminController", description = "Admin management of product category JSON Schemas")
public interface ProductCategorySchemaAdminApiSpec {

    @Operation(
            summary = "List all product category schemas (Admin)",
            description = "Returns every registered category schema, including disabled ones.",
            security = @SecurityRequirement(name = "keycloak")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Schemas retrieved successfully",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE, array = @ArraySchema(schema = @Schema(implementation = ProductCategorySchemaResponseDto.class)))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - JWT token missing or invalid",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - ADMIN role required",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    ResponseEntity<List<ProductCategorySchemaResponseDto>> getAll();

    @Operation(
            summary = "Get a product category schema by key (Admin)",
            security = @SecurityRequirement(name = "keycloak"),
            parameters = @Parameter(name = "categoryKey", description = "The category key to fetch", required = true)
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Schema retrieved successfully",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ProductCategorySchemaResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - JWT token missing or invalid",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - ADMIN role required",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Unknown categoryKey",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    ResponseEntity<ProductCategorySchemaResponseDto> getByCategoryKey(final String categoryKey);

    @Operation(
            summary = "Register a new product category (Admin)",
            description = """
                    Registers a brand-new product category: a `categoryKey`, a display `label`, and
                    a JSON Schema describing/validating the shape of that category's `attributes`
                    payload. The schema is compiled before saving — a malformed schema is rejected
                    with 400 and nothing is persisted. Once saved, `ProductValidationService`
                    validates every product create/update against it immediately — no redeploy.
                    """,
            security = @SecurityRequirement(name = "keycloak"),
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ProductCategorySchemaCreateRequestDto.class))
            )
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Schema registered successfully",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ProductCategorySchemaResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input / malformed JSON Schema / categoryKey already registered",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - JWT token missing or invalid",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - ADMIN role required",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    ResponseEntity<ProductCategorySchemaResponseDto> create(final ProductCategorySchemaCreateRequestDto request);

    @Operation(
            summary = "Update a product category schema (Admin)",
            description = "Patches label/jsonSchema/active. Only supplied fields are updated. A new jsonSchema is re-compiled before saving.",
            security = @SecurityRequirement(name = "keycloak"),
            parameters = @Parameter(name = "categoryKey", description = "The category key to update", required = true),
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ProductCategorySchemaUpdateRequestDto.class))
            )
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Schema updated successfully",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ProductCategorySchemaResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input / malformed JSON Schema / unknown categoryKey",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - JWT token missing or invalid",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - ADMIN role required",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    ResponseEntity<ProductCategorySchemaResponseDto> update(final String categoryKey, final ProductCategorySchemaUpdateRequestDto request);

    @Operation(
            summary = "Delete a product category schema (Admin)",
            security = @SecurityRequirement(name = "keycloak"),
            parameters = @Parameter(name = "categoryKey", description = "The category key to delete", required = true)
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Schema deleted successfully (No Content)"),
            @ApiResponse(responseCode = "400", description = "Unknown categoryKey",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - JWT token missing or invalid",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - ADMIN role required",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    ResponseEntity<Void> delete(final String categoryKey);
}
