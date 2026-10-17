package com.novatech.cybertech.dto.response.review;

import lombok.Builder;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Pair of {@code (productUuid, orderUuid)} — plus a couple of display-friendly fields —
 * representing one product the authenticated user is allowed to review now.
 *
 * <p>Returned as a list by {@code GET /api/v1/services/review/reviewable}. Each entry is a
 * (product, source order) tuple: a product bought twice surfaces as two entries (one per
 * order). The frontend uses this to enable / pre-fill the "Write a review" CTA on the
 * product page and to power an "Account → Pending reviews" screen.</p>
 */
@Builder
public record ReviewableProductDto(
        UUID productUuid,
        UUID orderUuid,
        String productName,
        LocalDate orderDate
) {
}
