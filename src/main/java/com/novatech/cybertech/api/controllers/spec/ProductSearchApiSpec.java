package com.novatech.cybertech.api.controllers.spec;

import com.novatech.cybertech.api.error.model.ErrorResponseDto;
import com.novatech.cybertech.dto.request.search.ProductSearchRequestDto;
import com.novatech.cybertech.dto.response.product.ProductAvailabilityDto;
import com.novatech.cybertech.dto.response.product.ProductResponseDto;
import com.novatech.cybertech.dto.response.search.ProductFacetsResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@Tag(name = "Product", description = "Public catalog endpoints: read products, search and best-sellers")
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
            description = "Searches products using filters such as name, category, brand, price range, attributes, etc. The optional sort (RELEVANCE, NAME_ASC, PRICE_ASC, PRICE_DESC, RATING_DESC) orders the whole result before pagination; an unknown value is a 400 MALFORMED_JSON.",
            requestBody = @RequestBody(required = true, description = "Search criteria for filtering products", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ProductSearchRequestDto.class))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "List of products matching the search criteria", content = @Content(mediaType = APPLICATION_JSON_VALUE, array = @ArraySchema(schema = @Schema(implementation = ProductResponseDto.class)))),
                    @ApiResponse(responseCode = "400", description = "Invalid search criteria", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))})
    Page<ProductResponseDto> searchProducts(final ProductSearchRequestDto productSearchRequestDto);

    @Operation(
            summary = "Catalog facets: filter values and their product counts (public)",
            description = """
                    Takes the same body as /search (`page` and `size` are ignored) and returns, in one call, the
                    values actually present among the matching products with their counts: brands, price bounds
                    and, when `category` is given, the values of each text attribute and the bounds of each
                    integer / number attribute of that category's JSON Schema (keys come from the schema).

                    Disjunctive counting: each facet is counted under every active filter except its own, so
                    ticking a brand keeps the other brands listed with their counts while the attribute counts
                    narrow to that brand. Keyword and category always apply. `totalHits` matches every filter,
                    like /search's `page.totalElements`. Values are sorted by count descending then
                    alphabetically, capped at 50 per facet; blank values are skipped. As on /search,
                    `attributes` and `numericRanges` are ignored when `category` is absent.

                    Public endpoint, no JWT required. Sent with a short `Cache-Control: public`.
                    """,
            requestBody = @RequestBody(required = true, description = "Search criteria, as for /search", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ProductSearchRequestDto.class))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Facets of the matching products", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ProductFacetsResponseDto.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid criteria (INVALID_REQUEST) or category without a schema (UNKNOWN_PRODUCT_CATEGORY)", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))})
    ResponseEntity<ProductFacetsResponseDto> getSearchFacets(final ProductSearchRequestDto productSearchRequestDto);

    @Operation(summary = "Live availability of a batch of products",
            description = """
                    Returns, for each existing product, the units still sellable right now (stock minus units
                    reserved by unpaid orders). Read live from the database and served with Cache-Control:
                    no-store — stock moves on every order. Unknown uuids are absent from the result.
                    """,
            parameters = {
                    @Parameter(name = "uuids", description = "1 to 50 product uuids, comma-separated or repeated", required = true,
                            array = @ArraySchema(schema = @Schema(implementation = UUID.class)))
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "Availability of the existing products", content = @Content(mediaType = APPLICATION_JSON_VALUE, array = @ArraySchema(schema = @Schema(implementation = ProductAvailabilityDto.class)))),
                    @ApiResponse(responseCode = "400", description = "Missing, malformed or more than 50 uuids", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))})
    ResponseEntity<List<ProductAvailabilityDto>> getAvailability(final Set<UUID> productUuids);

}
