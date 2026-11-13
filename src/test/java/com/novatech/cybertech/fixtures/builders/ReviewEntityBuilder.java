package com.novatech.cybertech.fixtures.builders;

import com.novatech.cybertech.entities.ReviewEntity;

import java.util.UUID;

/**
 * Test fixture builder for {@link ReviewEntity}. Presets {@code uuid} explicitly because builders
 * bypass {@code BaseEntity#prePersist}.
 */
public final class ReviewEntityBuilder {

    private ReviewEntityBuilder() {
    }

    public static ReviewEntity aValidReview() {
        return aValidReviewBuilder().build();
    }

    public static ReviewEntity.ReviewEntityBuilder<?, ?> aValidReviewBuilder() {
        return ReviewEntity.builder()
                .uuid(UUID.randomUUID())
                .rating(5)
                .comment("Great product")
                .isHateful(false)
                .productEntity(ProductEntityBuilder.aValidProduct())
                .userEntity(UserEntityBuilder.aValidUser());
    }
}
