package com.novatech.cybertech.api.controllers.spec;

import com.novatech.cybertech.api.error.model.ErrorResponseDto;
import com.novatech.cybertech.dto.response.product.ProductResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@Tag(name = "Recommendations", description = "Endpoints to read the connected user's personalized product recommendations")
public interface RecommendationControllerApiSpec {

    @Operation(summary = "Get my recommendations",
            description = "Returns up to n personalized product recommendations for the connected user, "
                    + "sourced from Gorse with a fallback to newest-catalog then best-sellers if Gorse is unavailable "
                    + "or has no history yet for this user.",
            security = @SecurityRequirement(name = "keycloak"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Recommendations retrieved successfully",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, array = @ArraySchema(schema = @Schema(implementation = ProductResponseDto.class)))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - User not logged in",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "500", description = "Internal Server Error",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
            })
    ResponseEntity<List<ProductResponseDto>> getMyRecommendations(
            @Parameter(hidden = true) @AuthenticationPrincipal final Jwt jwt,
            @Parameter(description = "Number of recommendations to return") final int n
    );
}
