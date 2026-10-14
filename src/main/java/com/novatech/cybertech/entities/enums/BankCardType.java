package com.novatech.cybertech.entities.enums;


public enum BankCardType {
    VISA,
    MASTERCARD,
    AMERICAN_EXPRESS,
    DISCOVER,
    OTHER;

    /**
     * Maps a Stripe card brand ({@code PaymentMethod.card.brand}) to the stored card type.
     * Unlisted brands (unionpay, jcb, diners, cartes_bancaires...) and {@code null} map to OTHER.
     */
    public static BankCardType fromStripeBrand(final String stripeBrand) {
        if (stripeBrand == null) {
            return OTHER;
        }
        return switch (stripeBrand) {
            case "visa" -> VISA;
            case "mastercard" -> MASTERCARD;
            case "amex" -> AMERICAN_EXPRESS;
            case "discover" -> DISCOVER;
            default -> OTHER;
        };
    }
}

