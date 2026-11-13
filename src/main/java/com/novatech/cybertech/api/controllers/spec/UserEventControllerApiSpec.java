package com.novatech.cybertech.api.controllers.spec;

import com.novatech.cybertech.api.error.model.ErrorResponseDto;
import com.novatech.cybertech.dto.request.event.UserEventBatchRequestDto;
import com.novatech.cybertech.dto.request.event.UserEventDto;
import com.novatech.cybertech.dto.response.userevent.UserEventBatchResponseDto;
import com.novatech.cybertech.dto.response.userevent.UserEventResponseDto;
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

@Tag(name = "User Events", description = "Ingestion of the browser-only user interaction events")
public interface UserEventControllerApiSpec {

    String ACCEPTED_TYPES = "Accepted eventType: VIEW, CLICK, COMPARE, SEARCH_RESULT_CLICK, SCROLL_DEPTH_HIGH, HOVER, "
            + "SEARCH_QUERY, IGNORE_RECOMMENDATION, BOUNCE. PURCHASE, ADD_TO_CART, REMOVE_FROM_CART, WISHLIST_ADD, "
            + "RATING_* and REVIEW_* are written by the backend when the action is validated and refused here (400).";

    @Operation(summary = "Ingest a browser event",
            description = "Receives one signal only the browser sees, for the recommendation engine. " + ACCEPTED_TYPES,
            security = @SecurityRequirement(name = "keycloak"),
            requestBody = @RequestBody(content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = UserEventDto.class))),
            responses = {
                    @ApiResponse(responseCode = "201", description = "Event accepted", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = UserEventResponseDto.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid event data, or an eventType produced by the backend (INVALID_REQUEST, fieldErrors on eventType); nothing is stored", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "404", description = "Unknown productId (PRODUCT_NOT_FOUND)", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "429", description = "Too many requests (TOO_MANY_REQUESTS), see Retry-After", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
            })
    @ResponseStatus(HttpStatus.CREATED)
    ResponseEntity<UserEventResponseDto> collectEvent(UserEventDto eventDto, Jwt jwt);

    @Operation(summary = "Ingest the browser events of a page",
            description = "Same as consume-event for up to 50 events. A forbidden eventType fails the whole batch (400, "
                    + "fieldErrors on events[i].eventType); an event of an unknown product is ignored and counted in 'ignored'. " + ACCEPTED_TYPES,
            security = @SecurityRequirement(name = "keycloak"),
            requestBody = @RequestBody(content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = UserEventBatchRequestDto.class))),
            responses = {
                    @ApiResponse(responseCode = "201", description = "Events stored", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = UserEventBatchResponseDto.class))),
                    @ApiResponse(responseCode = "400", description = "Empty batch, more than 50 events, invalid event or backend-only eventType (INVALID_REQUEST); nothing is stored", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "429", description = "Too many requests (TOO_MANY_REQUESTS), see Retry-After", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
            })
    @ResponseStatus(HttpStatus.CREATED)
    ResponseEntity<UserEventBatchResponseDto> collectEvents(UserEventBatchRequestDto batch, Jwt jwt);
}
