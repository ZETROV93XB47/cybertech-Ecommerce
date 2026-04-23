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
 * Unit tests for {@link OrderShippedEvent}. Single-arg ctor uses the payload as the Spring source.
 */
class OrderShippedEventTest {

    private OrderEventDto sampleDto() {
        return OrderEventDto.builder()
                .orderUuid(UUID.randomUUID())
                .totalAmount(new BigDecimal("100.00"))
                .orderStatus(OrderStatus.SHIPPED)
                .shippingType(ShippingType.STANDARD)
                .shippingProvider(ShippingProvider.DHL)
                .paymentAttemptStatus(PaymentAttemptStatus.SUCCESS)
                .build();
    }

    @Test
    void singleArgConstructorShouldUsePayloadAsSourceAndExposeIt() {
        OrderEventDto dto = sampleDto();

        OrderShippedEvent event = new OrderShippedEvent(dto);

        assertThat(event.getSource()).isSameAs(dto);
        assertThat(event.getOrderEventDto()).isSameAs(dto);
    }
}
