package com.novatech.cybertech.api.controllers.spec;

import com.novatech.cybertech.api.error.model.ErrorResponseDto;
import com.novatech.cybertech.dto.request.cart.CartCreateRequestDto;
import com.novatech.cybertech.dto.request.cart.CartItemRemoveRequestDto;
import com.novatech.cybertech.dto.response.cart.CartResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

public interface CartManagementControllerApiSpec {

    @Operation(summary = "Request a Cart by UUID",
            description = "Fetches a Cart's details based on their unique UUID.",
            parameters = {
                    @Parameter(name = "cartUuid", description = "UUID for searching a Cart", required = true, schema = @Schema(implementation = UUID.class))
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "Cart found successfully", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = CartResponseDto.class))),
                    @ApiResponse(responseCode = "400", description = "Bad request", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "403", description = "Operation forbidden", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "404", description = "Cart not found", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
            })
    ResponseEntity<CartResponseDto> getCartByUuid(final UUID cartUuid);


    @Operation(summary = "Create a new Cart",
            description = "Registers a new Cart in the system.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Cart data for creation. All fields are mandatory",
                    required = true,
                    content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = CartCreateRequestDto.class))
            ),
            responses = {
                    @ApiResponse(responseCode = "201", description = "Cart created successfully",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = CartResponseDto.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid input data",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "409", description = "Conflict - Cart already exists",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "500", description = "Internal server error during Cart creation",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
            })
    ResponseEntity<CartResponseDto> createCart(final CartCreateRequestDto cartCreateRequestDto);


    @Operation(summary = "Update an existing Cart by UUID",
            description = "Updates an existing cart's details based on their unique UUID. Fields not provided will not be updated.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Cart data for update. Only provide fields that need to be changed.", required = true, content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = CartItemRemoveRequestDto.class))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Cart updated successfully",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = CartResponseDto.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid input data / Validation error",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "403", description = "Operation forbidden",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "404", description = "Cart not found",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "500", description = "Internal server error during cart update",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
            })
    ResponseEntity<CartResponseDto> updateCart(final UUID cartUuid, final CartItemRemoveRequestDto cartItemRemoveRequestDto);


    @Operation(summary = "Delete a Cart by UUID",
            description = "Deletes a cart based on their unique UUID.",
            parameters = {
                    @Parameter(name = "cartUuid", description = "The UUID of the cart to delete", required = true, schema = @Schema(implementation = UUID.class))
            },
            responses = {
                    @ApiResponse(responseCode = "204", description = "Cart deleted successfully (No Content)"),
                    @ApiResponse(responseCode = "400", description = "Bad request (e.g., invalid UUID format)",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "403", description = "Operation forbidden",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "404", description = "Cart not found",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "500", description = "Internal server error during cart deletion",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
            })
    ResponseEntity<Void> deleteCartByUuid(final UUID cartUuid);

    @Operation(summary = "Get current user's cart",
            description = "Retrieves the shopping cart associated with the authenticated user.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Cart retrieved successfully", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = CartResponseDto.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "403", description = "Operation forbidden", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "404", description = "User not found", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
            })
    ResponseEntity<CartResponseDto> getCart(final Jwt jwt);

    @Operation(summary = "Clear current user's cart",
            description = "Removes all items from the authenticated user's cart.",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Cart cleared successfully"),
                    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "403", description = "Operation forbidden", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "404", description = "User or Cart not found", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
            })
    ResponseEntity<Void> clearCart(final Jwt jwt);

    @Operation(summary = "Add items to cart",
            description = "Adds items to the authenticated user's cart.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Details of items to add",
                    required = true,
                    content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = CartCreateRequestDto.class))
            ),
            responses = {
                    @ApiResponse(responseCode = "201", description = "Items added successfully", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = CartResponseDto.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "403", description = "Operation forbidden", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "404", description = "User or Product not found", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
            })
    ResponseEntity<CartResponseDto> addToCart(final CartCreateRequestDto cartCreateRequestDto, final Jwt jwt);

    @Operation(summary = "Remove item from cart",
            description = "Removes a specific product from the authenticated user's cart.",
            parameters = {
                    @Parameter(name = "productUuid", description = "UUID of the product to remove", required = true, schema = @Schema(implementation = UUID.class))
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "Item removed successfully", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = CartResponseDto.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid UUID format", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "403", description = "Operation forbidden", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "404", description = "User or Item not found", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
            })
    ResponseEntity<CartResponseDto> removeFromCart(final UUID uuid, final Jwt jwt);

    @Operation(summary = "Decrease item quantity",
            description = "Decreases the quantity of a specific item in the cart.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Details of item to decrease",
                    required = true,
                    content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = CartItemRemoveRequestDto.class))
            ),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Quantity decreased successfully", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = CartResponseDto.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "403", description = "Operation forbidden", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "404", description = "User or Item not found", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
            })
    ResponseEntity<CartResponseDto> decreaseQuantity(final CartItemRemoveRequestDto cartItemRemoveRequestDto, final Jwt jwt);
}
