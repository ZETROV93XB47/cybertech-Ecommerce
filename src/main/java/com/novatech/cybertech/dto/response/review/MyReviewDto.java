package com.novatech.cybertech.dto.response.review;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** One review of the caller in {@code GET /review/mine}. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "A review written by the authenticated user")
public class MyReviewDto {

    private UUID uuid;

    private UUID productUuid;

    @Schema(description = "Name of the reviewed product", example = "Laptop Pro 14")
    private String productName;

    @Schema(description = "Order the product was bought in; null for a review older than this field when no matching order was found.", nullable = true)
    private UUID orderUuid;

    @Schema(description = "Rating from 1 to 5.", example = "4")
    private Integer rating;

    @Schema(description = "Review text; null when only a rating was given.", nullable = true)
    private String comment;

    private LocalDateTime createdAt;

    @Schema(description = "Last modification; null if never modified.", nullable = true)
    private LocalDateTime updatedAt;
}
