package com.novatech.cybertech.api.controllers.spec;

import com.novatech.cybertech.api.error.model.ErrorResponseDto;
import com.novatech.cybertech.dto.request.order.OrderCancellationRequestDto;
import com.novatech.cybertech.dto.request.order.OrderPlacingRequestDto;
import com.novatech.cybertech.dto.request.order.OrderUpdateRequestDto;
import com.novatech.cybertech.dto.response.order.OrderResponseDto;
import com.novatech.cybertech.dto.response.order.OrderStatusDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@Tag(name = "Order", description = "Endpoints for placing, retrying, retrieving, updating, cancelling and deleting orders")
public interface OrderManagementControllerApiSpec {

    @Operation(
            summary = "Place a new order (authenticated users only)",
            description = """
                    Creates a new order for the authenticated user.
                    The request must include the list of products to purchase, quantities,
                    shipping address, and payment type.
                    Requires a valid JWT token in the Authorization header (Bearer token).
                    """,
            security = @SecurityRequirement(name = "bearerAuth"),
            requestBody = @RequestBody(
                    description = "Order creation data for the currently authenticated user.",
                    required = true,
                    content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = OrderPlacingRequestDto.class))
            ),
            responses = {
                    @ApiResponse(responseCode = "201", description = "Order placed successfully", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = OrderResponseDto.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid request or validation error", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - JWT token missing or invalid", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "403", description = "Forbidden - user not allowed to perform this operation", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "500", description = "Internal server error while placing the order", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
            }
    )
    ResponseEntity<OrderResponseDto> placeOrder(
            @Parameter(description = "OrderPlacingRequestDto for placing order", required = true) final OrderPlacingRequestDto orderPlacingRequestDto,
            @Parameter(hidden = true) final Jwt jwt
    );


    @Operation(
            summary = "Cancel an existing order (authenticated users only)",
            description = """
                    Cancels an order identified by its UUID.
                    The operation is allowed only if the order belongs to the authenticated user and is in a cancellable state.
                    Requires a valid JWT token in the Authorization header (Bearer token).
                    """,
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Order cancelled successfully", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = OrderResponseDto.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid order UUID or order cannot be cancelled", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - JWT token missing or invalid", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "403", description = "Forbidden - user not allowed to cancel this order", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "404", description = "Order not found", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "500", description = "Internal server error while cancelling the order", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
            }
    )
    OrderResponseDto cancelOrder(
            @Parameter(description = "DTO containing the UUID of the order to cancel", required = true) OrderCancellationRequestDto orderCancellationRequestDto,
            @Parameter(hidden = true) Jwt jwt
    );


    @Operation(
            summary = "Update an existing Order by UUID",
            description = """
                    Updates an existing order's details based on their unique UUID. Fields not provided will not be updated.
                    """,
            security = @SecurityRequirement(name = "bearerAuth"),
            requestBody = @RequestBody(
                    description = "Order data for update. Only provide fields that need to be changed.",
                    required = true,
                    content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = OrderUpdateRequestDto.class))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Order updated successfully",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = OrderResponseDto.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid input data / Validation error",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - JWT token missing or invalid",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "403", description = "Operation forbidden",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "404", description = "Order not found",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "500", description = "Internal server error during order update",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
            })
    ResponseEntity<OrderResponseDto> updateOrder(
            @Parameter(description = "DTO for updating an existing Order. Only provide fields that need to be changed.", required = true) OrderUpdateRequestDto orderUpdateRequestDto,
            @Parameter(hidden = true) Jwt jwt
    );

    @Operation(
            summary = "Retry payment for a failed order",
            description = "Retries the payment process for an order that is in PAYMENT_FAILED status. Checks stock availability before proceeding.",
            security = @SecurityRequirement(name = "bearerAuth"),
            parameters = {
                    @Parameter(name = "uuid", description = "The UUID of the order to retry payment for", required = true, schema = @Schema(implementation = UUID.class))
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "Payment retried successfully (Order Paid)", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = OrderResponseDto.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid request or Order not in FAILED state", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "404", description = "Order not found", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
            })
    ResponseEntity<OrderResponseDto> retryPayment(
            @Parameter(description = "UUID of the order", required = true) final UUID orderUuid,
            @Parameter(hidden = true) final Jwt jwt
    );

    @Operation(summary = "Request a Order by UUID",
            description = "Fetches a Order's details based on their unique UUID.",
            parameters = {
                    @Parameter(name = "uuid", description = "UUID for searching a Order", required = true, schema = @Schema(implementation = UUID.class))
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "Order found successfully", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = OrderResponseDto.class))),
                    @ApiResponse(responseCode = "400", description = "Bad request", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - JWT token missing or invalid", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "403", description = "Operation forbidden", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "404", description = "Order not found", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
            })
    ResponseEntity<OrderResponseDto> getOrderByUuid(final UUID orderUuid, @Parameter(hidden = true) final Jwt jwt);


    @Operation(summary = "Get the current status of an order by UUID",
            description = "Lightweight status read for the order-confirmation polling loop after a Stripe payment. Ownership-checked at the service layer.",
            security = @SecurityRequirement(name = "bearerAuth"),
            parameters = {
                    @Parameter(name = "uuid", description = "UUID of the order to read status for", required = true, schema = @Schema(implementation = UUID.class))
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "Order status retrieved successfully", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = OrderStatusDto.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - JWT token missing or invalid", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "403", description = "Operation forbidden - order does not belong to the authenticated user", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "404", description = "Order not found", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
            })
    ResponseEntity<OrderStatusDto> getOrderStatusByUuid(final UUID orderUuid, @Parameter(hidden = true) final Jwt jwt);


    @Operation(summary = "Place an auto-generated order (admin debug helper)",
            description = "Admin-only debug / load-test utility that forges an order from synthetic cart data via the data generator. Restricted to ADMIN to avoid letting any authenticated user spam orders against another's cart state (BUG-IDOR-D4).",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "201", description = "Auto-generated order placed successfully", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = OrderResponseDto.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - JWT token missing or invalid", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "403", description = "Forbidden - ADMIN role required", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "404", description = "Underlying resource (e.g., generated user/product) not found", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
            })
    ResponseEntity<OrderResponseDto> placeOrder2(@Parameter(hidden = true) final Jwt jwt);


    @Operation(summary = "Delete a Order by UUID",
            description = "Deletes a order based on their unique UUID.",
            security = @SecurityRequirement(name = "bearerAuth"),
            parameters = {
                    @Parameter(name = "uuid", description = "The UUID of the order to delete", required = true, schema = @Schema(implementation = UUID.class))
            },
            responses = {
                    @ApiResponse(responseCode = "204", description = "Order deleted successfully (No Content)"),
                    @ApiResponse(responseCode = "400", description = "Bad request (e.g., invalid UUID format)",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - JWT token missing or invalid",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "403", description = "Operation forbidden",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "404", description = "Order not found",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "500", description = "Internal server error during order deletion",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
            })
    ResponseEntity<Void> deleteOrderByUuid(final UUID orderUuid, final Jwt jwt);

}
