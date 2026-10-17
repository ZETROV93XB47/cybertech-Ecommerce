package com.novatech.cybertech.factory;

import com.novatech.cybertech.entities.enums.PaymentType;
import com.novatech.cybertech.services.core.PaymentAttemptProcessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mockito;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit coverage for {@link PaymentStrategyFactory}. Unlike the other factories in this package,
 * the registry is keyed on {@code Set<PaymentType>} (so a single processor can declare support
 * for multiple types). Lookup uses Stream.findFirst() against the entry whose Set contains the
 * requested type.
 *
 * SA4.1R note: this factory is the OUTLIER of the factory family — unknown keys throw
 * {@link IllegalArgumentException} (NOT the custom NoStrategyFoundForProcessingTheRequest the
 * orchestrator originally expected). Pinned here.
 */
class PaymentStrategyFactoryTest {

    @Nested
    @DisplayName("happy-path dispatch (every PaymentType)")
    class HappyPath {

        @ParameterizedTest(name = "{index} - resolves {0}")
        @EnumSource(PaymentType.class)
        @DisplayName("Each PaymentType resolves to the processor whose Set contains it")
        void dispatchesEachPaymentTypeToItsProcessor(final PaymentType type) {
            final PaymentAttemptProcessor processor = Mockito.mock(PaymentAttemptProcessor.class);
            final Map<Set<PaymentType>, PaymentAttemptProcessor> map = new HashMap<>();
            map.put(EnumSet.allOf(PaymentType.class), processor);
            final PaymentStrategyFactory factory = new PaymentStrategyFactory(map);

            assertThat(factory.getServiceFromPaymentType(type)).isSameAs(processor);
        }

        @Test
        @DisplayName("Multiple registered Sets: each is reachable via any of its members")
        void multipleSetsResolveIndependently() {
            final PaymentAttemptProcessor cardProcessor = Mockito.mock(PaymentAttemptProcessor.class, "card");
            final PaymentAttemptProcessor walletProcessor = Mockito.mock(PaymentAttemptProcessor.class, "wallet");
            final Map<Set<PaymentType>, PaymentAttemptProcessor> map = new HashMap<>();
            map.put(EnumSet.of(PaymentType.MASTERCARD, PaymentType.VISA), cardProcessor);
            map.put(EnumSet.of(PaymentType.APPLE_PAY, PaymentType.GOOGLE_PAY), walletProcessor);
            final PaymentStrategyFactory factory = new PaymentStrategyFactory(map);

            assertThat(factory.getServiceFromPaymentType(PaymentType.MASTERCARD)).isSameAs(cardProcessor);
            assertThat(factory.getServiceFromPaymentType(PaymentType.VISA)).isSameAs(cardProcessor);
            assertThat(factory.getServiceFromPaymentType(PaymentType.APPLE_PAY)).isSameAs(walletProcessor);
            assertThat(factory.getServiceFromPaymentType(PaymentType.GOOGLE_PAY)).isSameAs(walletProcessor);
        }
    }

    @Nested
    @DisplayName("misses & error behaviour")
    class Misses {

        @Test
        @DisplayName("No processor for type => throws IllegalArgumentException with the type name")
        void unknownTypeThrowsIllegalArgumentException() {
            final PaymentAttemptProcessor processor = Mockito.mock(PaymentAttemptProcessor.class);
            final Map<Set<PaymentType>, PaymentAttemptProcessor> map = new HashMap<>();
            map.put(EnumSet.of(PaymentType.MASTERCARD), processor);
            final PaymentStrategyFactory factory = new PaymentStrategyFactory(map);

            assertThatThrownBy(() -> factory.getServiceFromPaymentType(PaymentType.APPLE_PAY))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("APPLE_PAY");
        }

        @Test
        @DisplayName("Empty registry => throws IllegalArgumentException for any type")
        void emptyRegistryThrows() {
            final PaymentStrategyFactory factory = new PaymentStrategyFactory(new HashMap<>());

            assertThatThrownBy(() -> factory.getServiceFromPaymentType(PaymentType.MASTERCARD))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Lookup by null type returns the first processor whose Set contains null — typically throws")
        void nullPaymentTypeBehaviour() {
            // Set.contains(null) is allowed and returns false for EnumSet, so the stream yields no
            // match and the orElseThrow fires with "type: null".
            final PaymentAttemptProcessor processor = Mockito.mock(PaymentAttemptProcessor.class);
            final Map<Set<PaymentType>, PaymentAttemptProcessor> map = new HashMap<>();
            map.put(EnumSet.of(PaymentType.MASTERCARD), processor);
            final PaymentStrategyFactory factory = new PaymentStrategyFactory(map);

            assertThatThrownBy(() -> factory.getServiceFromPaymentType(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("null");
        }
    }

    @Nested
    @DisplayName("findFirst / dispatch ordering")
    class Ordering {

        @Test
        @DisplayName("If two Sets contain the same type, findFirst() picks one — last-write-wins behaviour pinned")
        void overlappingSetsPickFirstByIterationOrder() {
            // LinkedHashMap to make iteration order deterministic; first-inserted wins.
            final PaymentAttemptProcessor first = Mockito.mock(PaymentAttemptProcessor.class, "first");
            final PaymentAttemptProcessor second = Mockito.mock(PaymentAttemptProcessor.class, "second");
            final Map<Set<PaymentType>, PaymentAttemptProcessor> map = new LinkedHashMap<>();
            map.put(EnumSet.of(PaymentType.MASTERCARD), first);
            map.put(EnumSet.of(PaymentType.MASTERCARD, PaymentType.VISA), second);
            final PaymentStrategyFactory factory = new PaymentStrategyFactory(map);

            // findFirst on a LinkedHashMap yields the earliest insertion match.
            assertThat(factory.getServiceFromPaymentType(PaymentType.MASTERCARD)).isSameAs(first);
            // VISA is only in the second Set:
            assertThat(factory.getServiceFromPaymentType(PaymentType.VISA)).isSameAs(second);
        }
    }
}
