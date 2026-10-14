package com.novatech.cybertech.dto.response.review;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.web.PagedModel;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Published reviews of a product: rating summary over every published review, plus one page of them.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductReviewsResponseDto {

    @Schema(description = "Mean rating over every published review, 1 decimal; null when there is none.", example = "4.3", nullable = true)
    private BigDecimal averageRating;

    @Schema(description = "Number of published reviews.", example = "12")
    private long reviewCount;

    @Schema(description = "Published reviews per rating; keys \"1\" to \"5\" are always present.", example = "{\"1\": 0, \"2\": 1, \"3\": 1, \"4\": 4, \"5\": 6}")
    private Map<String, Long> distribution;

    @Schema(description = "One page of published reviews, newest first.")
    private PagedModel<PublicReviewDto> reviews;
}
