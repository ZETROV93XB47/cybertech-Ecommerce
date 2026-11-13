package com.novatech.cybertech.api.error.model;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.List;

/**
 * Structured, machine-readable payload of an error, inside {@link ErrorResponseDto#getDetails()}.
 * Only set by the errors that document it; today {@code NOT_ENOUGH_STOCK}.
 */
@Getter
@Builder
@AllArgsConstructor
@EqualsAndHashCode
@Schema(description = "Structured error payload. Present only for errors that document it (NOT_ENOUGH_STOCK).")
public class ErrorDetailsDto {

    @ArraySchema(arraySchema = @Schema(description = "Every product line in shortage (a cart write reports the first one only)"),
            schema = @Schema(implementation = StockShortageDto.class))
    private final List<StockShortageDto> items;
}
