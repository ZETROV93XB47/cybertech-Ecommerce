package com.novatech.cybertech.dto.response.search;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Catalog facets of a product search: the filter values actually present and their product counts.
 * Each facet is counted under every active filter except its own (disjunctive counting).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductFacetsResponseDto {

    @Schema(description = "Number of products matching every filter, the same as /search's page.totalElements.", example = "42")
    private long totalHits;

    @Schema(description = "Brands, by count descending then alphabetically.")
    private List<FacetValueDto> brands;

    @Schema(description = "Price bounds.")
    private RangeFacetDto price;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Values of each text attribute of the category's JSON Schema, by count descending then alphabetically. "
            + "Present only when a category is given; every text attribute of the schema is a key, possibly with an empty list.")
    private Map<String, List<FacetValueDto>> attributes;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Bounds of each integer / number attribute of the category's JSON Schema. Present only when a category is given.")
    private Map<String, RangeFacetDto> numericAttributes;
}
