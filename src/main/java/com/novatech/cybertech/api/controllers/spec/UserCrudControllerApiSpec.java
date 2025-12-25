package com.novatech.cybertech.api.controllers.spec;

import com.novatech.cybertech.api.error.model.ErrorResponseDto;
import com.novatech.cybertech.dto.request.user.UserCreateRequestDto;
import com.novatech.cybertech.dto.request.user.UserUpdateRequestDto;
import com.novatech.cybertech.dto.response.user.UserResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

public interface UserCrudControllerApiSpec {

    @Operation(summary = "Request a User by UUID",
            description = "Fetches a user's details based on their unique UUID.",
            parameters = {
                    @Parameter(name = "userUuid", description = "UUID for searching a user", required = true, schema = @Schema(implementation = UUID.class))
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "User found successfully", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = UserResponseDto.class))),
                    @ApiResponse(responseCode = "400", description = "Bad request (e.g., invalid UUID format)", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "403", description = "Operation forbidden", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "404", description = "User not found", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))})
    ResponseEntity<UserResponseDto> getUserByUuid(final UUID userUuid);


    @Operation(summary = "Create a new User",
            description = "Registers a new user in the system. Email must be unique.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "User data for creation. All fields are mandatory except address and birthDate (if configured as optional).",
                    required = true,
                    content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = UserCreateRequestDto.class))
            ),
            responses = {
                    @ApiResponse(responseCode = "201", description = "User created successfully",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = UserResponseDto.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid input data / Validation error (e.g., email format, missing fields, password too short)",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "409", description = "Conflict - User already exists (e.g., duplicate email)",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "500", description = "Internal server error during user creation",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
            })
    ResponseEntity<UserResponseDto> createUser(final UserCreateRequestDto userCreateRequestDto);


    @Operation(summary = "Update an existing User by UUID",
            description = "Updates an existing user's details based on their unique UUID. Fields not provided will not be updated or will be set to null if applicable by the backend logic.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "User data for update. Only provide fields that need to be changed.", required = true, content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = UserUpdateRequestDto.class))), responses = {
            @ApiResponse(responseCode = "200", description = "User updated successfully",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = UserResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input data / Validation error",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Operation forbidden",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error during user update",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    ResponseEntity<UserResponseDto> updateUser(final UserUpdateRequestDto userUpdateRequestDto);


    @Operation(summary = "Delete a User by UUID",
            description = "Deletes a user based on their unique UUID.",
            parameters = {
                    @Parameter(name = "userUuid", description = "The UUID of the user to delete", required = true, schema = @Schema(implementation = UUID.class))
            },
            responses = {
            @ApiResponse(responseCode = "204", description = "User deleted successfully (No Content)"),
            @ApiResponse(responseCode = "400", description = "Bad request (e.g., invalid UUID format)",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Operation forbidden",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error during user deletion",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    ResponseEntity<Void> deleteUserByUuid(final UUID userUuid);
}
