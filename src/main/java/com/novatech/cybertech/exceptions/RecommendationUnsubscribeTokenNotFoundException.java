package com.novatech.cybertech.exceptions;

/**
 * The unsubscribe link of a suggestion e-mail carries a token no user owns. Mapped to 404
 * {@code RECOMMENDATION_UNSUBSCRIBE_TOKEN_NOT_FOUND}.
 */
public class RecommendationUnsubscribeTokenNotFoundException extends RuntimeException {
    public RecommendationUnsubscribeTokenNotFoundException(String message) {
        super(message);
    }

    public RecommendationUnsubscribeTokenNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
