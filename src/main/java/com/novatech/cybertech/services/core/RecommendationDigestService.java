package com.novatech.cybertech.services.core;

import com.novatech.cybertech.dto.data.NotificationContext;
import com.novatech.cybertech.dto.data.RecommendationDigestPayload;

import java.util.Optional;
import java.util.UUID;

/**
 * Daily product-suggestion e-mail: picks the products for one user and handles the unsubscribe
 * link. Sending is left to the caller ({@code RecommendationDigestTasklet}).
 */
public interface RecommendationDigestService {

    /**
     * Picks the products of the user's suggestion e-mail and records them as sent, in its own
     * transaction: once this method returns, a rerun of the job won't e-mail the user again
     * the same day.
     *
     * <p>Candidates: Gorse's personalized suggestions first, then the best sellers. Excluded:
     * out-of-stock products, products the user already bought or has in the cart, and products
     * sent in the previous days.
     *
     * @return the notification to deliver, or empty when the user is inactive or unsubscribed,
     * already received an e-mail recently, or no product is left after the exclusions
     */
    Optional<NotificationContext<RecommendationDigestPayload>> prepareDigest(Long userId);

    /**
     * Stops the suggestion e-mails of the user owning {@code token} (link of each e-mail).
     * Idempotent: an already unsubscribed user stays unsubscribed.
     *
     * @throws com.novatech.cybertech.exceptions.RecommendationUnsubscribeTokenNotFoundException
     *         when no user owns the token
     */
    void unsubscribe(UUID token);
}
