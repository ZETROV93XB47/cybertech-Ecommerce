package com.novatech.cybertech.factory;

import com.novatech.cybertech.entities.enums.NotificationType;
import com.novatech.cybertech.services.core.AbstractNotification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mockito;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit coverage for {@link NotificationStrategyFactory} — Map-backed lookup keyed on
 * {@link NotificationType}. Mirrors {@link DiscountStrategyFactoryTest}.
 *
 * Per SA4.1R: this factory has NO null-guard nor throw-on-miss; it returns null for any
 * unknown / null key when the map is HashMap-backed. EnumMap.get(null) also returns null
 * (so null-key behaviour is consistent for both impls).
 *
 * Pin: HashMap allows storing null keys; EnumMap throws NullPointerException on put(null).
 */
class NotificationStrategyFactoryTest {

    @Nested
    @DisplayName("happy-path dispatch (every NotificationType)")
    class HappyPath {

        @ParameterizedTest(name = "{index} - resolves {0}")
        @EnumSource(NotificationType.class)
        @DisplayName("Each NotificationType resolves to its registered AbstractNotification")
        void dispatchesEachEnumToItsStrategy(final NotificationType type) {
            final Map<NotificationType, AbstractNotification> map = new EnumMap<>(NotificationType.class);
            for (final NotificationType t : NotificationType.values()) {
                map.put(t, Mockito.mock(AbstractNotification.class, t.name()));
            }
            final NotificationStrategyFactory factory = new NotificationStrategyFactory(map);

            final AbstractNotification result = factory.getStrategy(type);

            assertThat(result).isSameAs(map.get(type));
        }
    }

    @Nested
    @DisplayName("misses & null behaviour")
    class Misses {

        @Test
        @DisplayName("Missing entry returns null (no exception)")
        void missingEntryReturnsNull() {
            final Map<NotificationType, AbstractNotification> map = new EnumMap<>(NotificationType.class);
            map.put(NotificationType.ORDER_CONFIRMATION, Mockito.mock(AbstractNotification.class));
            final NotificationStrategyFactory factory = new NotificationStrategyFactory(map);

            assertThat(factory.getStrategy(NotificationType.SHIPPING_CONFIRMATION)).isNull();
        }

        @Test
        @DisplayName("Empty registry returns null for every NotificationType")
        void emptyRegistryReturnsNullForEveryType() {
            final NotificationStrategyFactory factory =
                    new NotificationStrategyFactory(new EnumMap<>(NotificationType.class));

            for (final NotificationType type : NotificationType.values()) {
                assertThat(factory.getStrategy(type)).isNull();
            }
        }

        @Test
        @DisplayName("EnumMap-backed factory: get(null) returns null (no NPE)")
        void enumMapNullKeyReturnsNull() {
            final NotificationStrategyFactory factory =
                    new NotificationStrategyFactory(new EnumMap<>(NotificationType.class));

            assertThat(factory.getStrategy(null)).isNull();
        }

        @Test
        @DisplayName("HashMap-backed factory: get(null) returns null (HashMap allows null key)")
        void hashMapNullKeyReturnsNull() {
            final NotificationStrategyFactory factory = new NotificationStrategyFactory(new HashMap<>());

            assertThat(factory.getStrategy(null)).isNull();
        }

        @Test
        @DisplayName("EnumMap.put(null, ...) throws NPE — documents the EnumMap contract pin")
        void enumMapPutNullThrowsNpe() {
            final EnumMap<NotificationType, AbstractNotification> map = new EnumMap<>(NotificationType.class);
            assertThatThrownBy(() -> map.put(null, Mockito.mock(AbstractNotification.class)))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("registration semantics")
    class Registration {

        @Test
        @DisplayName("Duplicate registration last-write-wins (BUG-095 shape pin)")
        void duplicateRegistrationLastWriteWins() {
            final AbstractNotification first = Mockito.mock(AbstractNotification.class, "first");
            final AbstractNotification second = Mockito.mock(AbstractNotification.class, "second");
            final Map<NotificationType, AbstractNotification> map = new EnumMap<>(NotificationType.class);
            map.put(NotificationType.ORDER_CONFIRMATION, first);
            map.put(NotificationType.ORDER_CONFIRMATION, second);

            final NotificationStrategyFactory factory = new NotificationStrategyFactory(map);

            assertThat(factory.getStrategy(NotificationType.ORDER_CONFIRMATION)).isSameAs(second);
        }
    }
}
