package com.novatech.cybertech.api.controllers.spec;

import com.novatech.cybertech.api.error.model.ErrorResponseDto;
import com.novatech.cybertech.dto.request.search.ProductSearchRequestDto;
import com.novatech.cybertech.dto.response.product.ProductResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

public interface ProductSearchApiSpec {

    @Operation(summary = "Request a Product by UUID",
            description = "Fetches a Product's details based on their unique UUID.",
            parameters = {
                    @Parameter(name = "productUuid", description = "UUID for searching a Product", required = true, schema = @Schema(implementation = UUID.class))
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "Product found successfully", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ProductResponseDto.class))),
                    @ApiResponse(responseCode = "400", description = "Bad request", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "404", description = "Product not found", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))})
    ResponseEntity<ProductResponseDto> getProductByUuid(final UUID productUuid);


    @Operation(summary = "Get best-selling products",
            description = "Returns products ranked by total quantity sold, descending. Paginated.",
            responses = {
            @ApiResponse(responseCode = "200", description = "Page of best-selling products", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ProductResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Bad request", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))})
    ResponseEntity<Page<ProductResponseDto>> getBestSellers(final Pageable pageable);

    @Operation(
            summary = "Search products",
            description = "Searches products using filters such as name, category, brand, price range, attributes, etc.",
            requestBody = @RequestBody(required = true, description = "Search criteria for filtering products", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ProductSearchRequestDto.class))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "List of products matching the search criteria", content = @Content(mediaType = APPLICATION_JSON_VALUE, array = @ArraySchema(schema = @Schema(implementation = ProductResponseDto.class)))),
                    @ApiResponse(responseCode = "400", description = "Invalid search criteria", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))})
    Page<ProductResponseDto> searchProducts(final ProductSearchRequestDto productSearchRequestDto);

}
