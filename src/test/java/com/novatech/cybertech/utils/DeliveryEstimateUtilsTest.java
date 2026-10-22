package com.novatech.cybertech.utils;

import com.novatech.cybertech.entities.valueObjects.DeliveryWindow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link DeliveryEstimateUtils}: business days Monday to Friday, handling day moved by the cut-off.
 * 2026-10-12 is a Monday.
 */
class DeliveryEstimateUtilsTest {

    private static final LocalTime CUTOFF = LocalTime.of(15, 0);
    private static final DeliveryWindow EXPRESS = new DeliveryWindow(1, 1, CUTOFF);
    private static final DeliveryWindow STANDARD = new DeliveryWindow(2, 3, null);

    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 12);
    private static final LocalDate TUESDAY = MONDAY.plusDays(1);
    private static final LocalDate WEDNESDAY = MONDAY.plusDays(2);
    private static final LocalDate THURSDAY = MONDAY.plusDays(3);
    private static final LocalDate FRIDAY = MONDAY.plusDays(4);
    private static final LocalDate SATURDAY = MONDAY.plusDays(5);
    private static final LocalDate NEXT_MONDAY = MONDAY.plusDays(7);
    private static final LocalDate NEXT_TUESDAY = MONDAY.plusDays(8);

    private static DeliveryEstimateUtils.DeliveryEstimate estimate(final DeliveryWindow window, final LocalDate day, final int hour, final int minute) {
        return DeliveryEstimateUtils.estimate(window, LocalDateTime.of(day, LocalTime.of(hour, minute)));
    }

    @Test
    @DisplayName("express ordered by the cut-off: delivered the next business day")
    void expressBeforeCutoff_nextDay() {
        assertThat(estimate(EXPRESS, MONDAY, 14, 59)).isEqualTo(new DeliveryEstimateUtils.DeliveryEstimate(TUESDAY, TUESDAY));
        // Exactly at the cut-off still counts as "by 15:00".
        assertThat(estimate(EXPRESS, MONDAY, 15, 0)).isEqualTo(new DeliveryEstimateUtils.DeliveryEstimate(TUESDAY, TUESDAY));
    }

    @Test
    @DisplayName("express ordered after the cut-off: handled the next business day, delivered the day after")
    void expressAfterCutoff_dayAfter() {
        assertThat(estimate(EXPRESS, MONDAY, 15, 1)).isEqualTo(new DeliveryEstimateUtils.DeliveryEstimate(WEDNESDAY, WEDNESDAY));
    }

    @Test
    @DisplayName("weekends are skipped: Friday before the cut-off arrives Monday, Friday after it arrives Tuesday")
    void weekendSkipped() {
        assertThat(estimate(EXPRESS, FRIDAY, 10, 0)).isEqualTo(new DeliveryEstimateUtils.DeliveryEstimate(NEXT_MONDAY, NEXT_MONDAY));
        assertThat(estimate(EXPRESS, FRIDAY, 16, 0)).isEqualTo(new DeliveryEstimateUtils.DeliveryEstimate(NEXT_TUESDAY, NEXT_TUESDAY));
    }

    @Test
    @DisplayName("an order placed on the weekend is handled on Monday")
    void weekendOrder_handledMonday() {
        assertThat(estimate(EXPRESS, SATURDAY, 9, 0)).isEqualTo(new DeliveryEstimateUtils.DeliveryEstimate(NEXT_TUESDAY, NEXT_TUESDAY));
    }

    @Test
    @DisplayName("no cut-off: any time of a business day is handled that day; min and max give a range")
    void noCutoff_range() {
        assertThat(estimate(STANDARD, MONDAY, 23, 30)).isEqualTo(new DeliveryEstimateUtils.DeliveryEstimate(WEDNESDAY, THURSDAY));
        assertThat(estimate(STANDARD, THURSDAY, 8, 0)).isEqualTo(new DeliveryEstimateUtils.DeliveryEstimate(NEXT_MONDAY, NEXT_TUESDAY));
    }

    @Test
    @DisplayName("a window with max below min, or negative, is rejected")
    void invalidWindow() {
        assertThatThrownBy(() -> new DeliveryWindow(3, 2, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DeliveryWindow(-1, 2, null)).isInstanceOf(IllegalArgumentException.class);
    }
}
