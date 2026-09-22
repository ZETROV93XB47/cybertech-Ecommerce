package com.novatech.cybertech.services.core;

import com.novatech.cybertech.dto.response.product.ProductResponseDto;

import java.util.List;

/**
 * Reads Gorse's precomputed personalized recommendations for a user, with a cascading fallback
 * (personalized → latest → best-sellers) so a Gorse outage degrades gracefully instead of
 * breaking the home page. See {@code RecommendationServiceImp} for the cascade detail.
 */
public interface RecommendationService {

    /**
     * @param userId the caller's Keycloak id (JWT subject) — same identity space as
     *               {@code UserEvent#userId} and {@code GorseUserDto#userId}
     * @param n      how many products to return
     */
    List<ProductResponseDto> getRecommendedProducts(String userId, int n);
}
