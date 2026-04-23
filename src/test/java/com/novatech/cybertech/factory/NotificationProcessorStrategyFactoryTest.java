package com.novatech.cybertech.factory;

import com.novatech.cybertech.entities.enums.CommunicationChanel;
import com.novatech.cybertech.services.core.NotificationProcessor;
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

/**
 * Unit coverage for {@link NotificationProcessorStrategyFactory} — Map-backed lookup keyed on
 * {@link CommunicationChanel}. Same dispatch contract as the other notification factory: returns
 * null for any unknown / null key. SA4.1R note: this is intentional and consistent with the rest
 * of the notification family (only PaymentStrategyFactory throws on miss).
 */
class NotificationProcessorStrategyFactoryTest {

    @Nested
    @DisplayName("happy-path dispatch")
    class HappyPath {

        @ParameterizedTest(name = "{index} - resolves {0}")
        @EnumSource(CommunicationChanel.class)
        @DisplayName("Each CommunicationChanel resolves to its registered processor")
        void dispatchesEachChannelToItsProcessor(final CommunicationChanel chanel) {
            final Map<CommunicationChanel, NotificationProcessor> map = new EnumMap<>(CommunicationChanel.class);
            for (final CommunicationChanel c : CommunicationChanel.values()) {
                map.put(c, Mockito.mock(NotificationProcessor.class, c.name()));
            }
            final NotificationProcessorStrategyFactory factory = new NotificationProcessorStrategyFactory(map);

            final NotificationProcessor result = factory.getStrategy(chanel);

            assertThat(result).isSameAs(map.get(chanel));
        }
    }

    @Nested
    @DisplayName("misses & null behaviour")
    class Misses {

        @Test
        @DisplayName("Missing entry returns null")
        void missingEntryReturnsNull() {
            final Map<CommunicationChanel, NotificationProcessor> map = new EnumMap<>(CommunicationChanel.class);
            map.put(CommunicationChanel.EMAIL, Mockito.mock(NotificationProcessor.class));
            final NotificationProcessorStrategyFactory factory = new NotificationProcessorStrategyFactory(map);

            assertThat(factory.getStrategy(CommunicationChanel.SMS)).isNull();
        }

        @Test
        @DisplayName("Empty registry returns null for every channel")
        void emptyRegistryReturnsNull() {
            final NotificationProcessorStrategyFactory factory =
                    new NotificationProcessorStrategyFactory(new EnumMap<>(CommunicationChanel.class));

            for (final CommunicationChanel c : CommunicationChanel.values()) {
                assertThat(factory.getStrategy(c)).isNull();
            }
        }

        @Test
        @DisplayName("EnumMap-backed factory: get(null) returns null (no NPE)")
        void enumMapNullKeyReturnsNull() {
            final NotificationProcessorStrategyFactory factory =
                    new NotificationProcessorStrategyFactory(new EnumMap<>(CommunicationChanel.class));

            assertThat(factory.getStrategy(null)).isNull();
        }

        @Test
        @DisplayName("HashMap-backed factory: get(null) returns null (HashMap allows null key)")
        void hashMapNullKeyReturnsNull() {
            final NotificationProcessorStrategyFactory factory =
                    new NotificationProcessorStrategyFactory(new HashMap<>());

            assertThat(factory.getStrategy(null)).isNull();
        }
    }

    @Nested
    @DisplayName("registration semantics")
    class Registration {

        @Test
        @DisplayName("Duplicate registration last-write-wins (BUG-095 shape pin)")
        void duplicateRegistrationLastWriteWins() {
            final NotificationProcessor first = Mockito.mock(NotificationProcessor.class, "first");
            final NotificationProcessor second = Mockito.mock(NotificationProcessor.class, "second");
            final Map<CommunicationChanel, NotificationProcessor> map = new EnumMap<>(CommunicationChanel.class);
            map.put(CommunicationChanel.EMAIL, first);
            map.put(CommunicationChanel.EMAIL, second);

            final NotificationProcessorStrategyFactory factory = new NotificationProcessorStrategyFactory(map);

            assertThat(factory.getStrategy(CommunicationChanel.EMAIL)).isSameAs(second);
        }

        @Test
        @DisplayName("Stored processor is returned by reference (no copying)")
        void processorIsReturnedByReference() {
            final NotificationProcessor processor = Mockito.mock(NotificationProcessor.class);
            final Map<CommunicationChanel, NotificationProcessor> map = new EnumMap<>(CommunicationChanel.class);
            map.put(CommunicationChanel.PUSH_NOTIFICATION, processor);
            final NotificationProcessorStrategyFactory factory = new NotificationProcessorStrategyFactory(map);

            assertThat(factory.getStrategy(CommunicationChanel.PUSH_NOTIFICATION)).isSameAs(processor);
        }
    }
}
