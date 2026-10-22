package com.novatech.cybertech.dto.response.search;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Bounds of a numeric facet. Both are null when no product matches.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RangeFacetDto {

    @Schema(description = "Smallest value among the matching products; null when none matches.", example = "8")
    private Double min;

    @Schema(description = "Largest value among the matching products; null when none matches.", example = "64")
    private Double max;
}
