package com.novatech.cybertech.services.core;


import com.novatech.cybertech.dto.request.review.ReviewCreateRequestDto;
import com.novatech.cybertech.dto.request.review.ReviewUpdateRequestDto;
import com.novatech.cybertech.dto.response.review.ProductReviewsResponseDto;
import com.novatech.cybertech.dto.response.review.ReviewResponseDto;
import com.novatech.cybertech.dto.response.review.MyReviewDto;
import com.novatech.cybertech.dto.response.review.ReviewableProductDto;

import org.springframework.data.domain.Pageable;

import org.springframework.data.web.PagedModel;
import java.util.List;
import java.util.UUID;

public interface ReviewManagementService {
    ReviewResponseDto getByUUID(final UUID uuid);

    ReviewResponseDto create(final ReviewCreateRequestDto reviewCreateRequestDto, final String keycloakId);

    ReviewResponseDto update(final ReviewUpdateRequestDto reviewUpdateRequestDto, final String keycloakId);

    void deleteByUUID(final UUID uuid, final String keycloakId);

    /**
     * Lists products the given user has bought (orders in {@code PAID / SHIPPED / DELIVERED})
     * but not yet reviewed. Each entry is a {@code (productUuid, orderUuid)} pair plus
     * display-friendly product name + order date. Same product purchased in two orders
     * yields two entries (one per order).
     */
    /**
     * One page of the caller's own reviews (moderated ones included), newest first by default.
     * Sorting is limited to {@code createdAt}, {@code updatedAt} and {@code rating}; any other
     * sort falls back to {@code createdAt,desc}.
     */
    PagedModel<MyReviewDto> getMyReviews(String keycloakId, Pageable pageable);

    List<ReviewableProductDto> getReviewableProducts(final String keycloakId);

    /**
     * Published reviews of a product (not flagged hateful) for its public page: average rating
     * (1 decimal, null without reviews), count and per-rating distribution over all of them, plus
     * the requested page, newest first. Only the page number and size of {@code pageable} are
     * used: the order is fixed.
     *
     * @throws com.novatech.cybertech.exceptions.ProductNotFoundException if no product has this uuid.
     */
    ProductReviewsResponseDto getPublishedReviewsOfProduct(final UUID productUuid, final Pageable pageable);
}
