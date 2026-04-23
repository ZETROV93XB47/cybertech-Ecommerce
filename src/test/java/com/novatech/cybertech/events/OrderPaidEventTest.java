package com.novatech.cybertech.events;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link OrderPaidEvent}. Both constructors covered:
 * the (source, uuid) variant and the convenience (uuid)-only variant which uses uuid as source.
 */
class OrderPaidEventTest {

    @Test
    void twoArgConstructorShouldExposeSourceAndUuid() {
        Object source = new Object();
        UUID uuid = UUID.randomUUID();

        OrderPaidEvent event = new OrderPaidEvent(source, uuid);

        assertThat(event.getSource()).isSameAs(source);
        assertThat(event.getOrderUUID()).isEqualTo(uuid);
    }

    @Test
    void singleArgConstructorShouldUseUuidAsSourceAndExposeIt() {
        UUID uuid = UUID.randomUUID();

        OrderPaidEvent event = new OrderPaidEvent(uuid);

        assertThat(event.getSource()).isSameAs(uuid);
        assertThat(event.getOrderUUID()).isEqualTo(uuid);
    }
}
