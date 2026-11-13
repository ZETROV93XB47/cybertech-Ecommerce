package com.novatech.cybertech.dto.response.review;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A published review as shown to anyone on the product page. Carries the author's first name only:
 * never their e-mail, last name or user uuid.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicReviewDto {

    private UUID uuid;

    @Schema(description = "Rating from 1 to 5.", example = "4")
    private Integer rating;

    @Schema(description = "Review text; null when the author left only a rating.", nullable = true)
    private String comment;

    @Schema(description = "Author's first name only; null when the account has none (show e.g. \"Client\").", example = "Camille", nullable = true)
    private String authorFirstName;

    @Schema(description = "Day the review was posted (ISO date).", example = "2026-10-14")
    private LocalDate createdAt;
}
