package com.novatech.cybertech.dto.response.search;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One value of a catalog facet and the number of products carrying it.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FacetValueDto {

    @Schema(description = "Facet value, as sent back in the search filters.", example = "LENOVO")
    private String value;

    @Schema(description = "Number of products with this value, under every active filter except this facet's own.", example = "12")
    private long count;
}
