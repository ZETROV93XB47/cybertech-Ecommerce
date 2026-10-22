package com.novatech.cybertech.exceptions;

/**
 * A discount campaign's {@code calculationType} requires a field (e.g. {@code percentage} for
 * {@code PERCENTAGE}, {@code fixedAmount} for {@code FIXED_AMOUNT}) that is missing — caught at
 * admin create/update time, before persisting, so {@code PercentageDiscountStrategy}/
 * {@code FixedAmountDiscountStrategy} never have to raise their own defensive
 * {@code IllegalStateException} (unmapped in {@code ErrorManagementController}) at price-calculation
 * time on a real order.
 */
public class DiscountCampaignMissingRequiredFieldException extends RuntimeException {
    public DiscountCampaignMissingRequiredFieldException(String message) {
        super(message);
    }

    public DiscountCampaignMissingRequiredFieldException(String message, Throwable cause) {
        super(message, cause);
    }
}
