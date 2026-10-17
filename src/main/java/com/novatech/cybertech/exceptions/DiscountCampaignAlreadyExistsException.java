package com.novatech.cybertech.exceptions;

/**
 * Admin tried to create a discount campaign under a key that is already taken (409).
 */
public class DiscountCampaignAlreadyExistsException extends RuntimeException {
    public DiscountCampaignAlreadyExistsException(final String message) {
        super(message);
    }

    public DiscountCampaignAlreadyExistsException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
