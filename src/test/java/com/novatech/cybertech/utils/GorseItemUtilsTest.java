package com.novatech.cybertech.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit tests for {@link GorseItemUtils}. */
class GorseItemUtilsTest {

    @Test
    @DisplayName("keeps Gorse's order and skips ids that are not UUIDs (including null)")
    void toProductUuids() {
        final UUID first = UUID.randomUUID();
        final UUID second = UUID.randomUUID();

        final List<UUID> uuids = GorseItemUtils.toProductUuids(
                Arrays.asList(first.toString(), "not-a-uuid", null, second.toString()));

        assertThat(uuids).containsExactly(first, second);
    }

    @Test
    @DisplayName("empty list → empty list")
    void empty() {
        assertThat(GorseItemUtils.toProductUuids(List.of())).isEmpty();
    }
}
