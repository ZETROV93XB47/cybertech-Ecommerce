package com.novatech.cybertech.events;

import com.novatech.cybertech.dto.data.OrderEventDto;
import com.novatech.cybertech.entities.enums.OrderStatus;
import com.novatech.cybertech.entities.enums.PaymentAttemptStatus;
import com.novatech.cybertech.entities.enums.ShippingProvider;
import com.novatech.cybertech.entities.enums.ShippingType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link OrderCreatedEvent}. Pure POJO/event-wrapper coverage — single (source, payload)
 * constructor + getter parity.
 */
class OrderCreatedEventTest {

    private OrderEventDto sampleDto() {
        return OrderEventDto.builder()
                .orderUuid(UUID.randomUUID())
                .totalAmount(new BigDecimal("100.00"))
                .orderStatus(OrderStatus.CREATED)
                .shippingType(ShippingType.STANDARD)
                .shippingProvider(ShippingProvider.DHL)
                .paymentAttemptStatus(PaymentAttemptStatus.CREATED)
                .build();
    }

    @Test
    void constructorWithSourceAndPayloadShouldExposeBothViaSpringApiAndGetter() {
        Object source = new Object();
        OrderEventDto dto = sampleDto();

        OrderCreatedEvent event = new OrderCreatedEvent(source, dto);

        assertThat(event.getSource()).isSameAs(source);
        assertThat(event.getOrderEventDto()).isSameAs(dto);
    }

    @Test
    void getterShouldReturnNullWhenPayloadWasNull() {
        Object source = new Object();
        OrderCreatedEvent event = new OrderCreatedEvent(source, null);
        assertThat(event.getOrderEventDto()).isNull();
        assertThat(event.getSource()).isSameAs(source);
    }
}
