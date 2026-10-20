package com.novatech.cybertech.api.controllers.spec;

import org.springframework.data.web.PagedModel;
import com.novatech.cybertech.dto.response.review.MyReviewDto;
import com.novatech.cybertech.api.error.model.ErrorResponseDto;
import com.novatech.cybertech.dto.request.review.ReviewCreateRequestDto;
import com.novatech.cybertech.dto.request.review.ReviewUpdateRequestDto;
import com.novatech.cybertech.dto.response.review.ProductReviewsResponseDto;
import com.novatech.cybertech.dto.response.review.ReviewResponseDto;
import com.novatech.cybertech.dto.response.review.ReviewableProductDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.UUID;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@Tag(name = "Review", description = "Endpoints for product review CRUD operations")
public interface ReviewCrudControllerApiSpec {

    @Operation(summary = "Request a Review by UUID",
            description = "Fetches a Review's details based on their unique UUID.",
            parameters = {
                    @Parameter(name = "reviewUuid", description = "UUID for searching a Review", required = true, schema = @Schema(implementation = UUID.class))
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "Review found successfully", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ReviewResponseDto.class))),
                    @ApiResponse(responseCode = "400", description = "Bad request", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "403", description = "Operation forbidden", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "404", description = "Review not found", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))})
    ResponseEntity<ReviewResponseDto> getReviewByUuid(final UUID reviewUuid);

    @Operation(summary = "Published reviews of a product (public)",
            description = """
                    Rating summary over every published review of the product (`averageRating` with 1 decimal,
                    null without reviews; `reviewCount`; `distribution` with keys "1" to "5" always present) and
                    one page of those reviews, newest first (`page`, `size` default 10, max 100; any `sort`
                    is ignored). A review rejected by moderation is never listed. Each review carries the
                    author's first name only, never their e-mail, last name or user id.

                    Public endpoint, no JWT required. Sent with a short `Cache-Control: public`.
                    """,
            parameters = {
                    @Parameter(name = "productUuid", description = "Product whose reviews to list", required = true, schema = @Schema(implementation = UUID.class))
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "Rating summary and one page of published reviews", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ProductReviewsResponseDto.class))),
                    @ApiResponse(responseCode = "400", description = "Malformed product uuid (METHOD_ARGUMENT_TYPE_MISMATCH)", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "404", description = "Unknown product (PRODUCT_NOT_FOUND)", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))})
    ResponseEntity<ProductReviewsResponseDto> getProductReviews(final UUID productUuid, @Parameter(hidden = true) final Pageable pageable);


    @Operation(summary = "Create a new Review",
            description = "Registers a new Review in the system. Requires authenticated USER role.",
            security = @SecurityRequirement(name = "keycloak"),
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Review data for creation. All fields are mandatory",
                    required = true,
                    content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ReviewCreateRequestDto.class))
            ),
            responses = {
                    @ApiResponse(responseCode = "201", description = "Review created successfully",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ReviewResponseDto.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid input data / Validation error",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - JWT token missing or invalid",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "403", description = "Forbidden - caller does not own the order being reviewed or comment moderation rejected",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "404", description = "Product / Order / User not found",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "500", description = "Internal server error during Review creation",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
            })
    ResponseEntity<ReviewResponseDto> createReview(final ReviewCreateRequestDto reviewCreateRequestDto, final Jwt jwt);


    @Operation(summary = "Update an existing Review by UUID",
            description = "Updates an existing review's details based on their unique UUID. Fields not provided will not be updated. Requires authenticated USER role and ownership of the review.",
            security = @SecurityRequirement(name = "keycloak"),
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Review data for update. Only provide fields that need to be changed.", required = true, content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ReviewUpdateRequestDto.class))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Review updated successfully",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ReviewResponseDto.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid input data / Validation error",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - JWT token missing or invalid",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "403", description = "Operation forbidden - caller is not the author of the review",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "404", description = "Review not found",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "500", description = "Internal server error during review update",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
            })
    ResponseEntity<ReviewResponseDto> updateReview(final ReviewUpdateRequestDto reviewUpdateRequestDto, final Jwt jwt);


    @Operation(summary = "Delete a Review by UUID",
            description = "Deletes a review based on their unique UUID. Requires authenticated USER role and ownership of the review.",
            security = @SecurityRequirement(name = "keycloak"),
            parameters = {
                    @Parameter(name = "reviewUuid", description = "The UUID of the review to delete", required = true, schema = @Schema(implementation = UUID.class))
            },
            responses = {
                    @ApiResponse(responseCode = "204", description = "Review deleted successfully (No Content)"),
                    @ApiResponse(responseCode = "400", description = "Bad request (e.g., invalid UUID format)",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - JWT token missing or invalid",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "403", description = "Operation forbidden - caller is not the author of the review",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "404", description = "Review not found",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "500", description = "Internal server error during review deletion",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
            })
    ResponseEntity<Void> deleteReviewByUuid(final UUID reviewUuid, final Jwt jwt);

    @Operation(summary = "List the caller's reviews (paginated)",
            description = """
                    Reviews written by the authenticated user, including those hidden by moderation, so they
                    can be edited or deleted. Defaults: page=0, size=10, sort=createdAt,desc. Sortable fields:
                    createdAt, updatedAt, rating; any other sort falls back to createdAt,desc.
                    """,
            security = @SecurityRequirement(name = "keycloak"),
            parameters = {
                    @Parameter(name = "page", description = "0-based page index (default 0)", required = false),
                    @Parameter(name = "size", description = "Page size (default 10)", required = false),
                    @Parameter(name = "sort", description = "property,direction — createdAt (default, desc), updatedAt or rating", required = false)
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "One page of the caller's reviews (possibly empty)"),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - JWT token missing or invalid",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "403", description = "Operation forbidden - USER role required",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
            })
    ResponseEntity<PagedModel<MyReviewDto>> getMyReviews(@Parameter(hidden = true) final Jwt jwt, @Parameter(hidden = true) final Pageable pageable);

    @Operation(summary = "List the products the caller can review",
            description = "Products the authenticated user has bought (order PAID, SHIPPED or DELIVERED) and not reviewed yet, with the order to reference in POST /create.",
            security = @SecurityRequirement(name = "keycloak"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Reviewable products (possibly empty)",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ReviewableProductDto.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - JWT token missing or invalid",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "403", description = "Operation forbidden - USER role required",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
            })
    ResponseEntity<List<ReviewableProductDto>> getReviewableProducts(final Jwt jwt);
}
