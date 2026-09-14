package com.novatech.cybertech.api.controllers.spec;

import com.novatech.cybertech.api.error.model.ErrorResponseDto;
import com.novatech.cybertech.dto.response.product.ProductCategorySchemaResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * Public (non-admin) contract for reading one category's JSON Schema — e.g. so an admin
 * product-creation form can render its fields dynamically instead of hardcoding them per category.
 */
@Tag(name = "ProductCategorySchemaController", description = "Public read access to product category JSON Schemas")
public interface ProductCategorySchemaApiSpec {

    @Operation(
            summary = "Get a product category's JSON Schema",
            description = "Returns the registered schema for the given category key. No authentication required.",
            parameters = @Parameter(name = "categoryKey", description = "The category key to fetch", required = true)
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Schema retrieved successfully",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ProductCategorySchemaResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Unknown categoryKey",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    ResponseEntity<ProductCategorySchemaResponseDto> getByCategoryKey(final String categoryKey);
}
