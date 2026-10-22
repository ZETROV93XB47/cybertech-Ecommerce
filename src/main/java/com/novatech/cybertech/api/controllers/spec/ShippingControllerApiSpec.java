package com.novatech.cybertech.api.controllers.spec;

import com.novatech.cybertech.api.error.model.ErrorResponseDto;
import com.novatech.cybertech.dto.response.shipping.ShippingOptionResponseDto;
import com.novatech.cybertech.entities.enums.ShippingProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * Public read-only contract for the shipping options. The matching implementation lives in
 * {@code com.novatech.cybertech.api.controllers.implementation.ShippingController} and is mounted
 * under {@code /api/v1/services/shipping}, whitelisted in {@code SecurityConfig#PUBLIC_URLS}.
 */
@Tag(name = "Shipping", description = "Public shipping options and fees")
public interface ShippingControllerApiSpec {

    @Operation(
            summary = "List shipping options and their fees (public)",
            description = """
                    Returns one entry per available (provider, type) pair, sorted by provider then
                    by cost. `cost` is exactly the `shippingCost` an order placed with that pair is
                    charged (same computation, rounded to 2 decimals), so a client can show shipping
                    fees before checkout without copying the tariffs.

                    Public endpoint — no JWT required. Cacheable for a short time (`Cache-Control: public`).
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Shipping options (empty when the requested provider is not available).",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE, array = @ArraySchema(schema = @Schema(implementation = ShippingOptionResponseDto.class)))),
            @ApiResponse(responseCode = "400", description = "Unknown `provider` value (METHOD_ARGUMENT_TYPE_MISMATCH)",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    ResponseEntity<List<ShippingOptionResponseDto>> getShippingOptions(
            @Parameter(description = "Restrict the list to one carrier; all carriers when omitted.") ShippingProvider provider);
}
