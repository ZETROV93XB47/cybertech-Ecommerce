package com.novatech.cybertech.utils;

import com.novatech.cybertech.entities.valueObjects.DeliveryWindow;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Indicative delivery dates of an order from its shipping option's {@link DeliveryWindow}. Business
 * days are Monday to Friday; public holidays are deliberately ignored (indicative dates only).
 *
 * <p>The order is handled the day it is placed if that day is a business day and it is placed by
 * the cut-off time (any time when there is none); otherwise the next business day. Delivery then
 * takes {@code min} to {@code max} business days from that handling day: an EXPRESS order placed
 * Monday before 15:00 arrives Tuesday, one placed Friday before 15:00 arrives Monday.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DeliveryEstimateUtils {

    private static final Set<DayOfWeek> WEEKEND = EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);

    /** Estimated delivery dates, both inclusive. */
    public record DeliveryEstimate(LocalDate from, LocalDate to) {
    }

    /**
     * @param orderedAt when the order was placed, in {@code CyberTechAppConstants.SHIPPING_TIME_ZONE}.
     */
    public static DeliveryEstimate estimate(final DeliveryWindow window, final LocalDateTime orderedAt) {
        final LocalDate handlingDay = handlingDay(window, orderedAt);
        return new DeliveryEstimate(
                plusBusinessDays(handlingDay, window.minBusinessDays()),
                plusBusinessDays(handlingDay, window.maxBusinessDays()));
    }

    private static LocalDate handlingDay(final DeliveryWindow window, final LocalDateTime orderedAt) {
        final LocalDate orderDay = orderedAt.toLocalDate();
        final boolean pastCutoff = window.cutoffTime() != null && orderedAt.toLocalTime().isAfter(window.cutoffTime());
        return isBusinessDay(orderDay) && !pastCutoff ? orderDay : nextBusinessDay(orderDay);
    }

    private static LocalDate plusBusinessDays(final LocalDate start, final int businessDays) {
        return Stream.iterate(start, DeliveryEstimateUtils::nextBusinessDay)
                .skip(businessDays)
                .findFirst()
                .orElseThrow();
    }

    private static LocalDate nextBusinessDay(final LocalDate day) {
        return Stream.iterate(day.plusDays(1), next -> next.plusDays(1))
                .filter(DeliveryEstimateUtils::isBusinessDay)
                .findFirst()
                .orElseThrow();
    }

    private static boolean isBusinessDay(final LocalDate day) {
        return !WEEKEND.contains(day.getDayOfWeek());
    }
}
