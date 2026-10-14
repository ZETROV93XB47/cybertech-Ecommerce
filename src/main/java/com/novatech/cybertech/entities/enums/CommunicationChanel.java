package com.novatech.cybertech.entities.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static java.util.Arrays.stream;

/**
 * Delivery channel of a notification. EMAIL and SMS are the user's favorite-channel choices;
 * PUSH_NOTIFICATION is not selectable: it only reaches the website, so it is always sent in
 * addition to the favorite channel to every browser the user subscribed.
 */
@Slf4j
@Getter
@RequiredArgsConstructor
public enum CommunicationChanel {
    EMAIL(1, true),
    SMS(2, true),
    PUSH_NOTIFICATION(3, false);

    private final Integer code;
    private final boolean userSelectable;

    /**
     * Channel to use as the user's favorite: falls back to EMAIL for accounts registered with
     * PUSH_NOTIFICATION before it stopped being selectable (or with no channel at all).
     */
    public static CommunicationChanel favoriteOrDefault(final CommunicationChanel favorite) {
        return favorite != null && favorite.isUserSelectable() ? favorite : EMAIL;
    }
}
