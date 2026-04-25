package com.novatech.cybertech.api.controllers.spec;

import com.novatech.cybertech.api.error.model.ErrorResponseDto;
import com.novatech.cybertech.dto.request.product.ProductCreateRequestDto;
import com.novatech.cybertech.dto.request.product.ProductUpdateRequestDto;
import com.novatech.cybertech.dto.response.product.ProductResponseDto;
import com.novatech.cybertech.entities.ProductEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@Tag(name = "Product Admin", description = "Admin-only endpoints for product CRUD and image upload")
public interface ProductManagementAdminApiSpec {

    @Operation(summary = "Get all Products (Admin)",
            description = "Fetches all products in the system.",
            security = @SecurityRequirement(name = "keycloak"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Products found successfully",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, array = @ArraySchema(schema = @Schema(implementation = ProductResponseDto.class)))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - JWT token missing or invalid",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "403", description = "Operation forbidden - ADMIN role required",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "500", description = "Internal server error",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
            })
    ResponseEntity<Page<ProductResponseDto>> getAllProducts(final Pageable pageable);

    @Operation(summary = "Create a new Product (Admin)",
            description = "Registers a new Product in the system.",
            security = @SecurityRequirement(name = "keycloak"),
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Product data for creation. All fields are mandatory",
                    required = true,
                    content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ProductCreateRequestDto.class))
            ),
            responses = {
                    @ApiResponse(responseCode = "201", description = "Product created successfully",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ProductResponseDto.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid input data / Validation error / product attributes not matching category constraints",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - JWT token missing or invalid",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "403", description = "Operation forbidden - ADMIN role required",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "500", description = "Internal server error during Product creation",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
            })
    ResponseEntity<ProductResponseDto> createProduct(final ProductCreateRequestDto productCreateRequestDto);

    @Operation(summary = "Update an existing Product by UUID (Admin)",
            description = "Updates an existing product's details based on their unique UUID. Fields not provided will not be updated.",
            security = @SecurityRequirement(name = "keycloak"),
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Product data for update. Only provide fields that need to be changed.", required = true, content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ProductUpdateRequestDto.class))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Product updated successfully",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ProductResponseDto.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid input data / Validation error / path-body UUID mismatch",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - JWT token missing or invalid",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "403", description = "Operation forbidden - ADMIN role required",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "404", description = "Product not found",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "500", description = "Internal server error during product update",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
            })
    ResponseEntity<ProductResponseDto> updateProduct(final UUID productUuid, final ProductUpdateRequestDto productUpdateRequestDto);

    @Operation(summary = "Delete a Product by UUID (Admin)",
            description = "Deletes a product based on their unique UUID.",
            security = @SecurityRequirement(name = "keycloak"),
            parameters = {
                    @Parameter(name = "productUuid", description = "The UUID of the product to delete", required = true, schema = @Schema(implementation = UUID.class))
            },
            responses = {
                    @ApiResponse(responseCode = "204", description = "Product deleted successfully (No Content)"),
                    @ApiResponse(responseCode = "400", description = "Bad request (e.g., invalid UUID format)",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - JWT token missing or invalid",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "403", description = "Operation forbidden - ADMIN role required",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "404", description = "Product not found",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "500", description = "Internal server error during product deletion",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
            })
    ResponseEntity<Void> deleteProductByUuid(final UUID productUuid);


    @Operation(summary = "Create a new Product with an image (Admin)",
            description = "Creates a new product and uploads an associated image. Requires multipart/form-data.",
            security = @SecurityRequirement(name = "keycloak"),
            responses = {
                    @ApiResponse(responseCode = "201", description = "Product created successfully",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ProductResponseDto.class))),
                    @ApiResponse(responseCode = "400", description = "Bad request / invalid product data / unsupported image MIME / image too large",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - JWT token missing or invalid",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "403", description = "Operation forbidden - ADMIN role required",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "500", description = "Internal server error",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
            })
    ResponseEntity<ProductResponseDto> createProductWithImage(
            @Valid @Parameter(description = "JSON representation of the product to create") ProductCreateRequestDto productCreateRequestDto,
            @Parameter(description = "Image file for the product") MultipartFile image);
}