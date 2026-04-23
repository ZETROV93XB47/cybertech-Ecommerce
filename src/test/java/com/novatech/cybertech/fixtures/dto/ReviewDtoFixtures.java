package com.novatech.cybertech.fixtures.dto;

import com.novatech.cybertech.dto.request.review.ReviewCreateRequestDto;
import com.novatech.cybertech.dto.request.review.ReviewUpdateRequestDto;
import com.novatech.cybertech.dto.response.review.ReviewResponseDto;

import java.util.UUID;

/**
 * Tiny DTO factories for the review surface.
 */
public final class ReviewDtoFixtures {

    private ReviewDtoFixtures() {
    }

    public static ReviewCreateRequestDto aValidCreateRequest() {
        return aValidCreateRequestBuilder().build();
    }

    public static ReviewCreateRequestDto.ReviewCreateRequestDtoBuilder aValidCreateRequestBuilder() {
        return ReviewCreateRequestDto.builder()
                .userUuid(UUID.randomUUID())
                .orderUuid(UUID.randomUUID())
                .productUuid(UUID.randomUUID())
                .rating(5)
                .comment("Great product");
    }

    public static ReviewUpdateRequestDto aValidUpdateRequest() {
        return aValidUpdateRequestBuilder().build();
    }

    public static ReviewUpdateRequestDto.ReviewUpdateRequestDtoBuilder aValidUpdateRequestBuilder() {
        return ReviewUpdateRequestDto.builder()
                .reviewUuid(UUID.randomUUID())
                .rating(4)
                .comment("Updated comment");
    }

    public static ReviewResponseDto aSampleReviewResponse() {
        return aSampleReviewResponseBuilder().build();
    }

    public static ReviewResponseDto.ReviewResponseDtoBuilder aSampleReviewResponseBuilder() {
        return ReviewResponseDto.builder()
                .uuid(UUID.randomUUID())
                .userUuid(UUID.randomUUID())
                .productUuid(UUID.randomUUID())
                .productName("Sample Product")
                .rating(5)
                .comment("Great product");
    }
}
