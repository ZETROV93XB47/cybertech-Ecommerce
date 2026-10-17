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
 * Unit tests for {@link OrderUpdatedEvent}. Both ctors covered.
 */
class OrderUpdatedEventTest {

    private OrderEventDto sampleDto() {
        return OrderEventDto.builder()
                .orderUuid(UUID.randomUUID())
                .totalAmount(new BigDecimal("100.00"))
                .orderStatus(OrderStatus.AWAITING_PAYMENT)
                .shippingType(ShippingType.EXPRESS)
                .shippingProvider(ShippingProvider.FEDEX)
                .paymentAttemptStatus(PaymentAttemptStatus.PROCESSING)
                .build();
    }

    @Test
    void twoArgConstructorShouldExposeSourceAndPayload() {
        Object source = new Object();
        OrderEventDto dto = sampleDto();

        OrderUpdatedEvent event = new OrderUpdatedEvent(source, dto);

        assertThat(event.getSource()).isSameAs(source);
        assertThat(event.getOrderEventDto()).isSameAs(dto);
    }

    @Test
    void singleArgConstructorShouldUsePayloadAsSourceAndExposeIt() {
        OrderEventDto dto = sampleDto();

        OrderUpdatedEvent event = new OrderUpdatedEvent(dto);

        assertThat(event.getSource()).isSameAs(dto);
        assertThat(event.getOrderEventDto()).isSameAs(dto);
    }
}
