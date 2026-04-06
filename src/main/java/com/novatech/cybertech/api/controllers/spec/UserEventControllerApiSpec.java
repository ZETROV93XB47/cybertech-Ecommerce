package com.novatech.cybertech.api.controllers.spec;

import com.novatech.cybertech.api.error.model.ErrorResponseDto;
import com.novatech.cybertech.dto.request.event.UserEventDto;
import com.novatech.cybertech.entities.document.UserEvent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ResponseStatus;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@Tag(name = "UserEventController", description = "Ingestion of user interaction events")
public interface UserEventControllerApiSpec {

    @Operation(summary = "Ingest a user event",
            description = "Receives user events (click, view, purchase) for recommendation engine.",
            security = @SecurityRequirement(name = "bearerAuth"),
            requestBody = @RequestBody(content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = UserEventDto.class))),
            responses = {
                    @ApiResponse(responseCode = "201", description = "Event accepted", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = UserEvent.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid event data", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
            })
    @ResponseStatus(HttpStatus.CREATED)
    ResponseEntity<UserEvent> collectEvent(UserEventDto eventDto, Jwt jwt);
}