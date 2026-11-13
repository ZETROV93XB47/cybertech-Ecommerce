package com.novatech.cybertech.utils;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Conversion of Gorse item ids (product UUIDs as strings) shared by the recommendation readers. */
@Slf4j
public final class GorseItemUtils {

    private GorseItemUtils() {
        // utility class — no instantiation
    }

    /**
     * Product UUIDs of the Gorse item ids, in Gorse's order. An id that is not a UUID is skipped
     * (logged as a WARN) instead of failing the whole list.
     */
    public static List<UUID> toProductUuids(final List<String> itemIds) {
        return itemIds.stream()
                .map(GorseItemUtils::parseUuid)
                .filter(Objects::nonNull)
                .toList();
    }

    private static UUID parseUuid(final String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException | NullPointerException e) {
            log.warn("Gorse returned a non-UUID item id {} — skipping", raw);
            return null;
        }
    }
}
