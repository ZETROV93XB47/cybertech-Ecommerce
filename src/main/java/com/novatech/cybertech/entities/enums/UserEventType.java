package com.novatech.cybertech.entities.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserEventType {

    // Explicit events (strong signals)
    PURCHASE(5.0, EventCategory.EXPLICIT_EVENT),
    ADD_TO_CART(3.0, EventCategory.EXPLICIT_EVENT),
    WISHLIST_ADD(2.5, EventCategory.EXPLICIT_EVENT),
    RATING_POSITIVE(2.0, EventCategory.EXPLICIT_EVENT),
    RATING_NEGATIVE(-2.0, EventCategory.EXPLICIT_EVENT),
    REVIEW_POSITIVE(2.5, EventCategory.EXPLICIT_EVENT),
    REVIEW_NEGATIVE(-2.5, EventCategory.EXPLICIT_EVENT),

    // Implicit events (weak signals)
    VIEW(0.5, EventCategory.IMPLICIT_EVENT),
    CLICK(1.0, EventCategory.IMPLICIT_EVENT),
    SCROLL_DEPTH_HIGH(0.8, EventCategory.IMPLICIT_EVENT),
    HOVER(0.3, EventCategory.IMPLICIT_EVENT),
    SEARCH_QUERY(0.2, EventCategory.IMPLICIT_EVENT),
    SEARCH_RESULT_CLICK(1.2, EventCategory.IMPLICIT_EVENT),

    // Negative events
    REMOVE_FROM_CART(-1.5, EventCategory.NEGATIVE_EVENT),
    IGNORE_RECOMMENDATION(-0.5, EventCategory.NEGATIVE_EVENT),
    BOUNCE(-0.3, EventCategory.NEGATIVE_EVENT);

    private final double score;
    private final EventCategory eventCategory;
}