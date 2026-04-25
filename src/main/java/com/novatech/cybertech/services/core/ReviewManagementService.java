package com.novatech.cybertech.services.core;


import com.novatech.cybertech.dto.request.review.ReviewCreateRequestDto;
import com.novatech.cybertech.dto.request.review.ReviewUpdateRequestDto;
import com.novatech.cybertech.dto.response.review.ReviewResponseDto;
import com.novatech.cybertech.dto.response.review.ReviewableProductDto;

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
    List<ReviewableProductDto> getReviewableProducts(final String keycloakId);
}
