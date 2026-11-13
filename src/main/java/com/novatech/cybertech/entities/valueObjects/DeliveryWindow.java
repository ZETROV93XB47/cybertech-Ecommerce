package com.novatech.cybertech.entities.valueObjects;

import java.time.LocalTime;

/**
 * Indicative delivery time of a (carrier, shipping type) pair, in business days (Monday to Friday,
 * public holidays ignored), counted from the day the order is handled.
 *
 * @param minBusinessDays earliest delivery, in business days.
 * @param maxBusinessDays latest delivery, in business days.
 * @param cutoffTime      local time ({@code CyberTechAppConstants.SHIPPING_TIME_ZONE}) after which an
 *                        order is handled the next business day; null when the same-day handling
 *                        has no time limit.
 */
public record DeliveryWindow(int minBusinessDays, int maxBusinessDays, LocalTime cutoffTime) {

    public DeliveryWindow {
        if (minBusinessDays < 0 || maxBusinessDays < minBusinessDays) {
            throw new IllegalArgumentException("Invalid delivery window: " + minBusinessDays + " to " + maxBusinessDays + " business days");
        }
    }
}
