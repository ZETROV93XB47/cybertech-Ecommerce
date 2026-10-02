package com.novatech.cybertech.exceptions;

/**
 * Admin back-office lookup of a discount campaign by a key that does not exist (404).
 * <p>
 * Distinct from {@link DiscountTypeNotActiveException}, which stays the checkout-side signal
 * (an order referencing an unknown / disabled / out-of-window campaign is a 400 bad request).
 */
public class DiscountCampaignNotFoundException extends RuntimeException {
    public DiscountCampaignNotFoundException(final String message) {
        super(message);
    }

    public DiscountCampaignNotFoundException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
