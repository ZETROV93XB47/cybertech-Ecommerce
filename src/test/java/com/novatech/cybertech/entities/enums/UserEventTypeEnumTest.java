package com.novatech.cybertech.entities.enums;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class UserEventTypeEnumTest {

    @Test
    void hasSixteenEvents() {
        assertThat(UserEventType.values()).hasSize(16);
    }

    @ParameterizedTest
    @EnumSource(UserEventType.class)
    void everyEventBindsACategory(final UserEventType event) {
        assertThat(event.getEventCategory()).isNotNull();
    }

    @Test
    void explicitEventsAllHaveAbsoluteScoreAtLeastTwo() {
        Arrays.stream(UserEventType.values())
                .filter(e -> e.getEventCategory() == EventCategory.EXPLICIT_EVENT)
                .forEach(e -> assertThat(Math.abs(e.getScore()))
                        .as("explicit event %s score magnitude", e.name())
                        .isGreaterThanOrEqualTo(2.0));
    }

    @Test
    void implicitEventsScoresFallInZeroOneRange() {
        Arrays.stream(UserEventType.values())
                .filter(e -> e.getEventCategory() == EventCategory.IMPLICIT_EVENT)
                .forEach(e -> assertThat(e.getScore())
                        .as("implicit event %s score", e.name())
                        .isBetween(0.0, 1.5));
    }

    @Test
    void negativeEventsHaveStrictlyNegativeScore() {
        Arrays.stream(UserEventType.values())
                .filter(e -> e.getEventCategory() == EventCategory.NEGATIVE_EVENT)
                .forEach(e -> assertThat(e.getScore())
                        .as("negative event %s score", e.name())
                        .isLessThan(0.0));
    }

    @Test
    void purchaseHasHighestPositiveExplicitScore() {
        assertThat(UserEventType.PURCHASE.getScore()).isEqualTo(5.0);
        assertThat(UserEventType.PURCHASE.getEventCategory()).isEqualTo(EventCategory.EXPLICIT_EVENT);
    }

    @Test
    void categoryDistributionMatchesDesign() {
        long explicit = Arrays.stream(UserEventType.values())
                .filter(e -> e.getEventCategory() == EventCategory.EXPLICIT_EVENT).count();
        long implicit = Arrays.stream(UserEventType.values())
                .filter(e -> e.getEventCategory() == EventCategory.IMPLICIT_EVENT).count();
        long negative = Arrays.stream(UserEventType.values())
                .filter(e -> e.getEventCategory() == EventCategory.NEGATIVE_EVENT).count();

        assertThat(explicit).isEqualTo(7);
        assertThat(implicit).isEqualTo(6);
        assertThat(negative).isEqualTo(3);
    }
}
