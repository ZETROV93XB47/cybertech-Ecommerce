package com.novatech.cybertech.config;

import com.novatech.cybertech.entities.enums.EventCategory;
import com.novatech.cybertech.entities.enums.UserEventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps the Gorse configuration mounted by docker-compose ({@code docker/gorse/config.toml}) in line
 * with {@link UserEventType}: Gorse feedback types are the enum names (GorseMapper), and an event
 * missing from {@code positive_feedback_types} is silently ignored by the collaborative filtering.
 */
class GorseConfigConsistencyTest {

    private static final Path GORSE_CONFIG = Path.of("src/main/resources/docker/gorse/config.toml");
    private static final Pattern QUOTED = Pattern.compile("\"([^\"]+)\"");

    private static List<String> listValue(final String toml, final String key) {
        final Matcher line = Pattern.compile("(?m)^\\s*" + key + "\\s*=\\s*\\[(.*)]").matcher(toml);
        assertThat(line.find()).as("%s in %s", key, GORSE_CONFIG).isTrue();
        final Matcher values = QUOTED.matcher(line.group(1));
        return values.results().map(m -> m.group(1)).toList();
    }

    @Test
    @DisplayName("every explicit event with a positive score is a Gorse positive feedback type, COMPARE included")
    void explicitPositiveEventsArePositiveFeedback() throws IOException {
        final List<String> positive = listValue(Files.readString(GORSE_CONFIG), "positive_feedback_types");

        final List<String> explicitPositive = Arrays.stream(UserEventType.values())
                .filter(e -> e.getEventCategory() == EventCategory.EXPLICIT_EVENT && e.getScore() > 0)
                .map(Enum::name)
                .toList();
        assertThat(positive).containsExactlyInAnyOrderElementsOf(explicitPositive).contains("COMPARE", "WISHLIST_ADD");
    }

    @Test
    @DisplayName("every configured feedback type is an existing UserEventType, none with a negative score")
    void configuredTypesExistAndAreNotNegative() throws IOException {
        final String toml = Files.readString(GORSE_CONFIG);
        final List<String> configured = new java.util.ArrayList<>(listValue(toml, "positive_feedback_types"));
        configured.addAll(listValue(toml, "read_feedback_types"));

        configured.forEach(name -> assertThat(UserEventType.valueOf(name).getScore()).as(name).isPositive());
    }
}
