package com.novatech.cybertech.services.implementation.support;

import com.novatech.cybertech.services.implementation.IdempotencyKeyServiceGeneratorImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link IdempotencyKeyServiceGeneratorImpl}.
 *
 * Pins {@code BUG-2505}: when {@code orderUUID} or {@code context} is null/empty, the implementation
 * falls back to a random UUID. Two calls for the same malformed request produce different keys —
 * defeating the very purpose of idempotency.
 */
class IdempotencyKeyServiceGeneratorImplTest {

    private final IdempotencyKeyServiceGeneratorImpl generator = new IdempotencyKeyServiceGeneratorImpl();

    private static final String ORDER_UUID = "550e8400-e29b-41d4-a716-446655440000";

    // ---------------- (String, List<String>) overload ----------------

    @Test
    @DisplayName("happy: same order + same context list yields the same SHA-256 hex key")
    void sameInputsProduceSameKey() {
        String k1 = generator.generateKey(ORDER_UUID, List.of("PLACE", "PAY"));
        String k2 = generator.generateKey(ORDER_UUID, List.of("PLACE", "PAY"));

        assertThat(k1).isEqualTo(k2);
        assertThat(k1).matches("[0-9a-f]{64}"); // SHA-256 hex
    }

    @Test
    @DisplayName("context list is sorted before hashing — [A,B] and [B,A] yield the same key")
    void contextOrderingDoesNotMatter() {
        String k1 = generator.generateKey(ORDER_UUID, List.of("ALPHA", "BETA"));
        String k2 = generator.generateKey(ORDER_UUID, List.of("BETA", "ALPHA"));

        assertThat(k1).isEqualTo(k2);
    }

    @Test
    @DisplayName("different order UUID yields a different key")
    void differentOrderUuidYieldsDifferentKey() {
        String k1 = generator.generateKey(ORDER_UUID, List.of("PLACE"));
        String k2 = generator.generateKey(UUID.randomUUID().toString(), List.of("PLACE"));

        assertThat(k1).isNotEqualTo(k2);
    }

    @Test
    @DisplayName("different context yields a different key for the same order UUID")
    void differentContextYieldsDifferentKey() {
        String k1 = generator.generateKey(ORDER_UUID, List.of("PLACE"));
        String k2 = generator.generateKey(ORDER_UUID, List.of("REFUND"));

        assertThat(k1).isNotEqualTo(k2);
    }

    @Test
    @DisplayName("BUG-2505: null orderUuid -> random non-idempotent key (pin)")
    void nullOrderUuidProducesRandomNonIdempotentKey() {
        String k1 = generator.generateKey(null, List.of("PLACE"));
        String k2 = generator.generateKey(null, List.of("PLACE"));

        assertThat(k1).as("BUG-2505: null inputs lose idempotency").isNotEqualTo(k2);
        // The random fallback returns a UUID string, not a SHA-256 hex.
        assertThat(k1).containsPattern("[0-9a-f-]+");
    }

    @Test
    @DisplayName("BUG-2505: null context -> random non-idempotent key (pin)")
    void nullContextProducesRandomNonIdempotentKey() {
        String k1 = generator.generateKey(ORDER_UUID, (List<String>) null);
        String k2 = generator.generateKey(ORDER_UUID, (List<String>) null);

        assertThat(k1).as("BUG-2505: null context loses idempotency").isNotEqualTo(k2);
    }

    @Test
    @DisplayName("BUG-2505: empty context -> random non-idempotent key (pin)")
    void emptyContextProducesRandomNonIdempotentKey() {
        String k1 = generator.generateKey(ORDER_UUID, Collections.emptyList());
        String k2 = generator.generateKey(ORDER_UUID, Collections.emptyList());

        assertThat(k1).as("BUG-2505: empty context loses idempotency").isNotEqualTo(k2);
    }

    // ---------------- (String, String) default overload ----------------

    @Test
    @DisplayName("default (String, String) overload routes through the (String, List) implementation")
    void stringOverloadRoutesThroughListImplementation() {
        String k1 = generator.generateKey(ORDER_UUID, "PAY");
        String k2 = generator.generateKey(ORDER_UUID, List.of("PAY"));

        assertThat(k1).isEqualTo(k2);
    }

    @Test
    @DisplayName("default (String, String) overload is repeatable")
    void stringOverloadIsRepeatable() {
        String k1 = generator.generateKey(ORDER_UUID, "REFUND");
        String k2 = generator.generateKey(ORDER_UUID, "REFUND");

        assertThat(k1).isEqualTo(k2);
        assertThat(k1).matches("[0-9a-f]{64}");
    }
}
