package com.novatech.cybertech.entities.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserEventType {

    // browserSignal = true: only the browser sees the signal, so POST /events/consume-event(s)
    // accepts it. false: the backend writes it itself when the business action is validated
    // (purchase, cart, wishlist, review) and consume-event refuses it, so a client cannot fake one.

    // Explicit events (strong signals)
    PURCHASE(5.0, EventCategory.EXPLICIT_EVENT, false),
    ADD_TO_CART(3.0, EventCategory.EXPLICIT_EVENT, false),
    WISHLIST_ADD(2.5, EventCategory.EXPLICIT_EVENT, false),
    // Product added to the comparator (2 to 3 products of one category): as strong a signal as a wishlist add.
    COMPARE(2.5, EventCategory.EXPLICIT_EVENT, true),
    RATING_POSITIVE(2.0, EventCategory.EXPLICIT_EVENT, false),
    RATING_NEGATIVE(-2.0, EventCategory.EXPLICIT_EVENT, false),
    REVIEW_POSITIVE(2.5, EventCategory.EXPLICIT_EVENT, false),
    REVIEW_NEGATIVE(-2.5, EventCategory.EXPLICIT_EVENT, false),

    // Implicit events (weak signals)
    VIEW(0.5, EventCategory.IMPLICIT_EVENT, true),
    CLICK(1.0, EventCategory.IMPLICIT_EVENT, true),
    SCROLL_DEPTH_HIGH(0.8, EventCategory.IMPLICIT_EVENT, true),
    HOVER(0.3, EventCategory.IMPLICIT_EVENT, true),
    SEARCH_QUERY(0.2, EventCategory.IMPLICIT_EVENT, true),
    SEARCH_RESULT_CLICK(1.2, EventCategory.IMPLICIT_EVENT, true),

    // Negative events
    REMOVE_FROM_CART(-1.5, EventCategory.NEGATIVE_EVENT, false),
    IGNORE_RECOMMENDATION(-0.5, EventCategory.NEGATIVE_EVENT, true),
    BOUNCE(-0.3, EventCategory.NEGATIVE_EVENT, true);

    private final double score;
    private final EventCategory eventCategory;
    private final boolean browserSignal;
}